package com.ecs160;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ParseAndCreate {

    public List<Post> parsePosts(String resourceName) throws Exception {
        List<Post> posts = new ArrayList<>();

        InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(resourceName)
        );

        JsonElement element = JsonParser.parseReader(reader);

        if (element.isJsonObject()) {
            JsonObject jsonObject = element.getAsJsonObject();
            // Assuming the JSON contains a "feed" array
            JsonArray feedArray = jsonObject.get("feed").getAsJsonArray();
            int postIdCounter = 0;

            for (JsonElement feedElement : feedArray) {
                JsonObject feedObject = feedElement.getAsJsonObject();

                // Check if this feed object contains a "thread" key
                if (feedObject.has("thread")) {
                    JsonObject threadObject = feedObject.getAsJsonObject("thread");
                    JsonObject postObject = threadObject.getAsJsonObject("post");
                    JsonObject recordObject = postObject.getAsJsonObject("record");

                    // Extract the main post's text content
                    String postContent = recordObject.get("text").getAsString();
                    Post mainPost = new Post();
                    mainPost.setPostId(postIdCounter++);
                    mainPost.setPostContent(postContent);

                    // process replies
                    List<Post> repliesList = new ArrayList<>();
                    JsonArray repliesArray = threadObject.getAsJsonArray("replies");

                    for (JsonElement replyElem : repliesArray) {
                        JsonObject replyObject = replyElem.getAsJsonObject();

                        if (!replyObject.has("post") || replyObject.get("post").isJsonNull()) {
                            continue;
                        }

                        JsonObject replyPostObject = replyObject.getAsJsonObject("post");

                        if (!replyPostObject.has("record") || replyPostObject.get("record").isJsonNull()) {
                            continue;
                        }

                        JsonObject replyRecord = replyPostObject.getAsJsonObject("record");

                        String replyContent = replyRecord.get("text").getAsString();

                        Post replyPost = new Post();
                        replyPost.setPostId(postIdCounter++);
                        replyPost.setPostContent(replyContent);
                        repliesList.add(replyPost);
                    }

                    mainPost.setReplies(repliesList);
                    posts.add(mainPost);

                }
            }
        }

        // Close the reader before returning
        reader.close();
        return posts;
    }
}
