package com.ecs160;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ParseAndCreate {

    public List<Post> parsePosts(String resourceName) throws Exception {
        List<Post> posts = new ArrayList<>();

        // Open the resource file from the classpath
        InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(resourceName)
        );

        // Parse the JSON content
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

                    // Process replies if present
                    List<Post> repliesList = new ArrayList<>();
                    if (threadObject.has("replies") && threadObject.get("replies").isJsonArray()) {
                        JsonArray repliesArray = threadObject.getAsJsonArray("replies");
                        int replyIdCounter = 0;
                        for (JsonElement replyElem : repliesArray) {
                            JsonObject replyObject = replyElem.getAsJsonObject();

                            // Check if the reply object contains a non-null "record" element
                            if (!replyObject.has("record") || replyObject.get("record").isJsonNull()) {
                                continue; // Skip this reply if there's no valid "record" data
                            }

                            JsonObject replyRecord = replyObject.getAsJsonObject("record");
                            String replyContent = replyRecord.get("text").getAsString();

                            Post replyPost = new Post();
                            replyPost.setPostId(replyIdCounter);
                            replyPost.setPostContent(replyContent);
                            repliesList.add(replyPost);
                            String replyId = String.valueOf(replyIdCounter);
                            replyIdCounter++;
                        }
                    }

                    // Create the main Post object with its replies
                    Post mainPost = new Post();
                    mainPost.setPostId(postIdCounter);
                    mainPost.setPostContent(postContent);
                    mainPost.setReplies(repliesList);
                    List<String> replyIds = new ArrayList<>();
                    if (mainPost.getReplies() != null) {
                        for (int i = 0; i < mainPost.getReplies().size(); i++) {
                            replyIds.add(mainPost.getPostId() + "_" + i);
                        }
                    }
                    mainPost.setReplyIds(replyIds);
                    posts.add(mainPost);
                    // Increment for next post
                    postIdCounter++;
                }
            }
        }

        // Close the reader before returning
        reader.close();
        return posts;
    }
}
