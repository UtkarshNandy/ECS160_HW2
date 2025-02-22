package com.ecs160;

import com.ecs160.persistence.Persistable;
import com.ecs160.persistence.PersistableField;
import com.ecs160.persistence.PersistableId;
import com.ecs160.persistence.PersistableListField;

import java.util.List;

@Persistable
public class Post {
    @PersistableId
    private int postId;
    @PersistableField
    private String postContent;
    @PersistableListField(className = "com.ecs160.Post")
    private List<Post> replies;

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public List<Post> getReplies() {
        return replies;
    }

    public void setReplies(List<Post> replies) {
        this.replies = replies;
    }

    public String getPostContent() {
        return postContent;
    }

    public void setPostContent(String postContent) {
        this.postContent = postContent;
    }

}

