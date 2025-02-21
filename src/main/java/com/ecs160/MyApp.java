package com.ecs160;


import com.ecs160.persistence.Session;

import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

public class MyApp {
    public static void main(String[] args) throws Exception {
        // code to parse JSON and create objects
        String resourceName = "input.json";
        ParseAndCreate newParser = new ParseAndCreate();
        List<Post> newPosts = newParser.parsePosts(resourceName);
        Post post = newPosts.get(2);
        Post reply = post.getReplies().get(0);
        System.out.println("post content: " + post.getPostContent());
        System.out.println("reply content: " + reply.getPostContent());
//        System.out.println("post id: " + post.getPostId());
//        System.out.println("post content: " + post.getPostContent());
//        System.out.println("post replies: " + post.getReplies());
//        System.out.println("post replyIds: " + post.getReplyIds());
        // create session object and add posts to it
        Session session = new Session();
//        for(Post post : newPosts){
//            session.add(post);
//        }
//        session.persistAll();



    }
}
