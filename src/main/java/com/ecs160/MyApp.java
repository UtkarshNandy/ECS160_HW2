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
        System.out.println("Persist complete.");


    }
}
