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

        Session session = new Session();

        for(Post post : newPosts){
            session.add(post);
        }

        session.persistAll();

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter post id: ");
        int postId = Integer.parseInt(scanner.nextLine());

        // 5. Create a partial Post object with only the ID set.
        Post partialPost = new Post();
        partialPost.setPostId(postId);

        // 6. Load the full post from Redis.
        Post fullPost = (Post) session.load(partialPost);
        if (fullPost == null) {
            System.out.println("No post found with id: " + postId);
        } else {
            // 7. Print the post content and its replies.
            System.out.println("> " + fullPost.getPostContent());
            if (fullPost.getReplies() != null) {
                for (Post reply : fullPost.getReplies()) {
                    System.out.println("  --> " + reply.getPostContent());
                }
            }
        }

        scanner.close();


    }
}
