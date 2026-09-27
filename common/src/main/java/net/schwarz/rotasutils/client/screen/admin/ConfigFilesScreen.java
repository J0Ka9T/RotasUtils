package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.*;
import net.schwarz.rotasutils.network.*;
import java.util.*;

@Environment(EnvType.CLIENT)
public final class ConfigFilesScreen extends RotasScreen implements AdminResponseReceiver {
    private final UUID session = UUID.randomUUID();
    private CompoundTag state = new CompoundTag();
    private String query = "", message = "Choose a domain to review its saved draft.";
    private boolean pending, loaded;
    private long requestId, sentAt;
    public ConfigFilesScreen(Screen parent) { super("Configuration files and drafts", parent); }
    @Override protected void buildContent() {
        guiWidth = Ui.fill(width, 780); guiHeight = Ui.fill(height, 460); guiLeft = (width-guiWidth)/2; guiTop=(height-guiHeight)/2;
        EditBox search = new EditBox(font, guiLeft+12, guiTop+36, guiWidth-100, 18, net.schwarz.rotasutils.client.screen.Ui.text("Search domains"));
        search.setValue(query); search.setResponder(v -> query=v); addRenderableWidget(search);
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Find"), b -> rebuild()).bounds(guiLeft+guiWidth-80,guiTop+35,68,20).build());
        List<String> domains = new ArrayList<>(); var values=state.getList("domains",8);
        for (int i=0;i<values.size();i++) { if (values.getString(i).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) { domains.add(values.getString(i)); } }
        ScrollPanel panel = new ScrollPanel(guiLeft+12,guiTop+64,guiWidth-24,Math.max(24,guiHeight-162),24);
        panel.setRows(domains.size(),(g,i,x,y,w,h,hover)->g.drawString(font,font.plainSubstrByWidth(domains.get(i),w-8),x+4,y+7,Ui.TEXT,false),
                (i,b)-> { if (!pending) { minecraft.setScreen(new ConfigReviewScreen(this,domains.get(i))); } }); registerPanel(panel);
        int col=(guiWidth-32)/3;
        String[] labels={"Import configuration files","Export active configuration","Back"};
        for(int i=0;i<3;i++) { int index=i; addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(labels[i]),b->{if(index==2){goBack();}else{request(index==0?"config_import_files":"config_export");}})
                .bounds(guiLeft+12+i*(col+4),guiTop+guiHeight-28,col,20).build()).active=!pending && (i==2 || state.getBoolean(i==0?"EDIT":"AUDIT")); }
        if(!loaded&&!pending){request("config_list");}
    }
    private void request(String action) {
        CompoundTag tag=new CompoundTag();tag.putUUID("ui",session);tag.putString("action",action);
        try{pending=true;sentAt=System.currentTimeMillis();requestId=ClientAdminNetwork.send(tag);}catch(RuntimeException e){failed(e.getMessage());}
    }
    private void rebuild(){clearWidgets();clearPanels();buildContent();}
    @Override public void receive(long id,CompoundTag response){if(id!=requestId||!response.hasUUID("ui")||!session.equals(response.getUUID("ui"))){return;}state=response;pending=false;loaded=true;rebuild();}
    @Override public void failed(String error){pending=false;message=error;}
    @Override public void tick(){if(pending&&System.currentTimeMillis()-sentAt>15000){failed("Request timed out. Refresh before retrying.");}}
    @Override protected void renderContent(GuiGraphics g,int mx,int my,float delta){
        var lines=state.getList("lines",8);int y=guiTop+guiHeight-90;
        if(lines.isEmpty()){g.drawString(font,font.plainSubstrByWidth(message,guiWidth-24),guiLeft+12,y,Ui.TEXT_MUTED,false);}
        else{for(int i=0;i<Math.min(4,lines.size());i++){g.drawString(font,font.plainSubstrByWidth(lines.getString(i),guiWidth-24),guiLeft+12,y+i*12,Ui.TEXT_MUTED,false);}}
    }
}
