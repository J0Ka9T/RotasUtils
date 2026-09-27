import os
import subprocess
import threading
import time
import re
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "build/rpg-smoke-server"
COMMAND = (ROOT / "build/rpg-server-command.txt").read_text(encoding="utf-8-sig").strip()
LOG = ROOT / "build/rpg-console-smoke.log"
lines = []
changed = threading.Condition()
durable_revision = None


def wait_for(fragment, start=0, timeout=90):
    deadline = time.monotonic() + timeout
    with changed:
        while True:
            if any(fragment in line for line in lines[start:]):
                return
            failure = next((line.strip() for line in lines[start:] if "_SMOKE_FAIL" in line), None)
            if failure:
                raise AssertionError(f"{failure}; inspect {LOG}")
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AssertionError(f"Timed out waiting for {fragment}; inspect {LOG}")
            changed.wait(min(remaining, 1))


def send(process, command):
    start = len(lines)
    process.stdin.write(command + "\n")
    process.stdin.flush()
    return start


def run(restart=False):
    global durable_revision
    environment = os.environ.copy()
    environment["MOD_CLASSES"] = os.pathsep.join(
        "rotasutils%%" + str(ROOT / path)
        for path in ("forge/build/classes/java/main", "forge/build/resources/main")
    )
    environment["MOD_CLASSES"] += os.pathsep + os.pathsep.join(
        "rotasutils_smoke%%" + str(ROOT / path)
        for path in ("forge/build/classes/java/rpgSmoke", "forge/build/resources/rpgSmoke")
    )
    process = subprocess.Popen(COMMAND, cwd=WORK, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                               env=environment,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    def consume():
        with LOG.open("a", encoding="utf-8") as log:
            for line in process.stdout:
                log.write(line)
                log.flush()
                with changed:
                    lines.append(line)
                    changed.notify_all()
    first = len(lines)
    reader = threading.Thread(target=consume, daemon=True)
    reader.start()
    malformed = WORK / "config/rotasutils/packs/server/welcome/bad-smoke.json"
    try:
        wait_for("RotasUtils content store loaded", first)
        pos = send(process, "rotas_smoke")
        wait_for("ROTAS_PHASE2_SMOKE_PASS", pos)
        pos = send(process, "rotas_admin_async_smoke")
        wait_for("ROTAS_ADMIN_PERMISSION_RECHECK_PASS", pos)
        if restart:
            pos = send(process, "rotas_usability_restart_smoke")
            wait_for("ROTAS_USABILITY_RESTART_PASS", pos)
            pos = send(process, "rotas_monster_restart_smoke")
            wait_for("ROTAS_MONSTER_RESTART_PASS", pos)
        else:
            pos = send(process, "rotas_usability_smoke")
            wait_for("ROTAS_USABILITY_SMOKE_PASS", pos)
            pos = send(process, "rotas_monster_smoke")
            wait_for("ROTAS_MONSTER_SMOKE_PASS", pos)
            pos = send(process, "rotas_item_smoke")
            wait_for("ROTAS_ITEM_SMOKE_PASS", pos)
            print("PASS: items, rarities, sets, requirements, deterministic loot, mailbox recovery", flush=True)
            pos = send(process, "rotas_boss_smoke")
            wait_for("ROTAS_BOSS_SMOKE_PASS", pos)
            print("PASS: boss phases, arena reset, contribution ledger, payout gating", flush=True)
            pos = send(process, "rotas_quest_economy_smoke")
            wait_for("ROTAS_QUEST_ECONOMY_SMOKE_PASS", pos)
            print("PASS: quest stages, resets, bounty limit, merchant stock and trade atomicity", flush=True)
            pos = send(process, "rotas_console_smoke")
            wait_for("ROTAS_CONSOLE_SMOKE_PASS", pos)
            print("PASS: console snapshot, quest/shop/mail operations and operator gating", flush=True)
        print("PASS: monster " + ("state restored after restart" if restart
                                  else "assignment, scaling, storage, clear, kill reward"), flush=True)
        pos = send(process, "rotas debug")
        wait_for("RPG revision=", pos)
        baseline = int(re.search(r"RPG revision=(\d+)", next(line for line in lines[pos:] if "RPG revision=" in line)).group(1))
        if restart and baseline != durable_revision:
            raise AssertionError("Rollback revision was not restored after restart")
        pos = send(process, "rotas debug content rotas:rule/welcome")
        wait_for("rotas:rule/welcome RULE enabled=true layer=SERVER", pos)
        pos = send(process, "rotas validate")
        wait_for("RPG packs valid: 4 definitions", pos)
        if not restart:
            malformed.write_text('{"schema":1,"schema":2}', encoding="utf-8")
            pos = send(process, "rotas reload")
            wait_for("RPG validation failed; active content retained", pos)
            pos = send(process, "rotas debug")
            wait_for("RPG revision=" + str(baseline), pos)
            malformed.unlink()
            pos = send(process, "rotas reload")
            wait_for("RPG packs applied: 4 definitions", pos)
            pos = send(process, "rotas debug")
            wait_for("RPG revision=", pos)
            baseline = int(re.search(r"RPG revision=(\d+)", next(line for line in lines[pos:] if "RPG revision=" in line)).group(1))
            send(process, "rotas admin draft discard")
            pos = send(process, "rotas admin draft create")
            wait_for("Draft created from revision " + str(baseline), pos)
            definition = {"schema": 1, "id": "rotas:stat/admin_smoke", "kind": "stat",
                          "body": {"base": 0, "per_level": 1, "min": 0, "max": 100}}
            pos = send(process, "rotas admin draft put " + json.dumps(definition, separators=(",", ":")))
            wait_for("Draft updated", pos)
            pos = send(process, "rotas admin draft preview")
            wait_for("Added: 1 Changed: 0 Removed: 0", pos)
            pos = send(process, "rotas admin draft validate")
            wait_for("Validation succeeded", pos)
            pos = send(process, "rotas admin draft apply")
            wait_for("Apply succeeded; active revision " + str(baseline + 1), pos)
            pos = send(process, "rotas admin draft create")
            wait_for("Draft created", pos)
            invalid = {"schema": 1, "id": "rotas:reward/admin_bad", "kind": "reward",
                       "body": {"actions": ["rotas:missing"]}}
            pos = send(process, "rotas admin draft put " + json.dumps(invalid, separators=(",", ":")))
            wait_for("Draft updated", pos)
            pos = send(process, "rotas admin draft apply")
            wait_for("Apply rejected; live content retained", pos)
            pos = send(process, "rotas admin draft discard")
            wait_for("Draft discarded", pos)
            pos = send(process, "rotas admin rollback " + str(baseline))
            wait_for("Rollback succeeded; active revision " + str(baseline + 2), pos)
            durable_revision = baseline + 2
            pos = send(process, "rotas admin history")
            wait_for("rollback:" + str(baseline), pos)
            pos = send(process, "rotas admin draft create")
            wait_for("Draft created", pos)
            pos = send(process, "rotas admin draft put " + json.dumps(definition, separators=(",", ":")))
            wait_for("Draft updated", pos)
        else:
            pos = send(process, "rotas admin status")
            wait_for("Draft base=" + str(durable_revision) + " edit=1 definitions=5", pos)
            pos = send(process, "rotas admin draft discard")
            wait_for("Draft discarded", pos)
        print("PASS: admin drafts, validation, revision, rollback" + (", persisted draft and rollback restart" if restart else ""), flush=True)
        pos = send(process, "save-all flush")
        wait_for("Saved the game", pos)
        send(process, "stop")
        process.stdin.close()
        dev_cleanup = False
        try:
            process.wait(timeout=8)
        except subprocess.TimeoutExpired:
            dump = subprocess.check_output([r"C:\Program Files\Java\jdk-17\bin\jcmd.exe",
                                            str(process.pid), "Thread.print"], text=True, encoding="utf-8")
            (ROOT / "build/rpg-smoke-shutdown-threads.txt").write_text(dump, encoding="utf-8")
            if '"Server thread"' in dump or '"Rotas-content-loader"' in dump:
                raise AssertionError("Server/kernel thread failed to terminate")
            if '"pool-2-thread-' not in dump:
                raise AssertionError("Unexpected JVM shutdown blocker; inspect thread dump")
            print("NOTE: server and kernel threads stopped; cleaning up Architectury development classpath workers", flush=True)
            process.kill()
            process.wait(timeout=10)
            dev_cleanup = True
        if process.returncode != 0 and not dev_cleanup:
            raise AssertionError(f"Server exited with {process.returncode}")
        print("PASS: " + ("restart, reload definitions, server-thread shutdown" if restart else
                         "startup, console diagnostics, validation, rejected reload retains revision, recovery, save, stop"), flush=True)
    finally:
        if malformed.exists():
            malformed.unlink()
        if process.poll() is None:
            try:
                send(process, "stop")
                process.wait(timeout=20)
            except (OSError, ValueError, subprocess.TimeoutExpired):
                process.kill()
                process.wait()
        reader.join(timeout=2)


if __name__ == "__main__":
    LOG.write_text("", encoding="utf-8")
    run()
    run(restart=True)
