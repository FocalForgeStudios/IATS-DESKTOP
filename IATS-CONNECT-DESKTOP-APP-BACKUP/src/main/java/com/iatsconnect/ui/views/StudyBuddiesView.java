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

/** Study partner directory based on course, cohort and availability. */
public final class StudyBuddiesView implements View {
    public String id(){return "study-buddies";} public String title(){return "Study Buddies";} public String navLabel(){return "Study Buddies";} public String icon(){return "♙";}
    public Access access(){return Access.SIGNED_IN;}
    public Node build(ViewContext ctx, Map<String,Object> args){
        UiKit ui=ctx.ui(); VBox page=new VBox(16,ui.label("Find a study partner",26,true),ui.note("Share your course and study availability. Only signed-in students can view the directory."));
        TextField course=new TextField(); course.setPromptText("Course"); TextField cohort=new TextField(); cohort.setPromptText("Cohort / intake"); TextField schedule=new TextField(); schedule.setPromptText("Availability (e.g. weekdays after 4pm)"); TextArea message=new TextArea(); message.setPromptText("What would you like to study together?"); message.setPrefRowCount(2);
        Label status=ui.note("Your request is visible to signed-in students.");
        VBox form=ui.card("Your study-buddy request"); form.getChildren().addAll(course,cohort,schedule,message,ui.button("Publish / update request",true,()->{
            if(course.getText().isBlank()||schedule.getText().isBlank()){status.setText("Course and study availability are required.");return;}
            Map<String,Object> p=new LinkedHashMap<>();p.put("user_id",ctx.user().userId());p.put("course",course.getText().trim());p.put("cohort",cohort.getText().trim());p.put("study_schedule",schedule.getText().trim());p.put("message",message.getText().trim());p.put("status","open");
            status.setText("Saving request…");ctx.async(()->ctx.api().rest("POST","study_buddy_requests?on_conflict=user_id",ctx.api().json(p)),ok->{status.setText("Your request is available to other students.");load(ctx,listing);},e->{status.setText("Could not save request: "+ApiException.wrap(e).userMessage());ctx.showError("Save study-buddy request",e);});
        }),status); page.getChildren().add(form);
        listing=ui.card("Students looking for study partners");page.getChildren().add(listing);load(ctx,listing);return page;
    }
    private VBox listing;
    private void load(ViewContext ctx,VBox list){UiKit ui=ctx.ui();list.getChildren().setAll(ui.label("Students looking for study partners",16,true),ui.note("Loading directory…"));ctx.async(()->ctx.api().rest("GET","study_buddy_requests?select=id,user_id,course,cohort,study_schedule,message,created_at&status=eq.open&order=created_at.desc",null),n->{list.getChildren().setAll(ui.label("Students looking for study partners",16,true));if(!n.isArray()||n.isEmpty()){list.getChildren().add(ui.note("No open requests yet. Be the first to post one."));return;}for(JsonNode x:n){VBox c=ui.card(x.path("course").asText("Course"));c.getChildren().add(ui.note("Cohort: "+x.path("cohort").asText("Not specified")));c.getChildren().add(ui.note("Availability: "+x.path("study_schedule").asText("Not specified")));if(!x.path("message").asText("").isBlank())c.getChildren().add(ui.note(x.path("message").asText()));if(!x.path("user_id").asText().equals(ctx.user().userId()))c.getChildren().add(ui.button("Message this student",false,()->ctx.navigate("messages",Map.of("recipientId",x.path("user_id").asText()))));list.getChildren().add(c);}},e->{list.getChildren().setAll(ui.label("Directory unavailable",16,true),ui.banner(ApiException.wrap(e).userMessage(),true),ui.button("Retry",false,()->load(ctx,list)));});}
}
