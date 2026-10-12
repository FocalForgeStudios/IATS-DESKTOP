package com.iatsconnect.ui.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.iatsconnect.services.ApiException;
import com.iatsconnect.ui.UiKit;
import com.iatsconnect.ui.View;
import com.iatsconnect.ui.ViewContext;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Direct messages. Recipient is selected using a student UUID copied from the student directory/profile. */
public final class MessagesView implements View {
 public String id(){return "messages";} public String title(){return "Messages";} public String navLabel(){return "Messages";} public String icon(){return "▱";} public Access access(){return Access.SIGNED_IN;}
 public Node build(ViewContext ctx,Map<String,Object> args){UiKit ui=ctx.ui();VBox page=new VBox(16,ui.label("Direct messages",26,true),ui.note("Choose a student ID and send a private message. Only the sender and recipient can read the conversation."));TextField recipient=new TextField(String.valueOf(args.getOrDefault("recipientId","")));recipient.setPromptText("Recipient student UUID");TextArea body=new TextArea();body.setPromptText("Write a message…");body.setPrefRowCount(3);Label status=ui.note("Messages are saved in Supabase.");VBox send=ui.card("Send a message");send.getChildren().addAll(recipient,body,ui.button("Send message",true,()->{if(recipient.getText().isBlank()||body.getText().isBlank()){status.setText("Recipient ID and message are required.");return;}if(recipient.getText().trim().equals(ctx.user().userId())){status.setText("You cannot message yourself.");return;}Map<String,Object> p=new LinkedHashMap<>();p.put("sender_id",ctx.user().userId());p.put("recipient_id",recipient.getText().trim());p.put("body",body.getText().trim());status.setText("Sending…");ctx.async(()->ctx.api().rest("POST","direct_messages",ctx.api().json(p)),ok->{status.setText("Message sent.");body.clear();load(ctx,recipient.getText().trim(),history);},e->{status.setText("Message not sent: "+ApiException.wrap(e).userMessage());ctx.showError("Send message",e);});}),status);page.getChildren().add(send);history=ui.card("Conversation");page.getChildren().add(history);if(!recipient.getText().isBlank())load(ctx,recipient.getText().trim(),history);else history.getChildren().add(ui.note("Enter a recipient UUID to view your conversation."));return page;}
 private VBox history;
 private void load(ViewContext ctx,String other,VBox box){UiKit ui=ctx.ui();box.getChildren().setAll(ui.label("Conversation",16,true),ui.note("Loading messages…"));String me=ctx.user().userId();String path="direct_messages?select=id,sender_id,recipient_id,body,created_at&or=(and(sender_id.eq."+me+",recipient_id.eq."+other+"),and(sender_id.eq."+other+",recipient_id.eq."+me+"))&order=created_at.asc&limit=200";ctx.async(()->ctx.api().rest("GET",path,null),n->{box.getChildren().setAll(ui.label("Conversation",16,true));if(!n.isArray()||n.isEmpty()){box.getChildren().add(ui.note("No messages yet."));return;}for(JsonNode m:n){String who=m.path("sender_id").asText().equals(me)?"You":"Student";VBox c=ui.card(who+" · "+m.path("created_at").asText(""));Label body=ui.label(m.path("body").asText(),13,false);body.setWrapText(true);c.getChildren().add(body);box.getChildren().add(c);}},e->{box.getChildren().setAll(ui.label("Could not load conversation",16,true),ui.banner(ApiException.wrap(e).userMessage(),true));});}
}
