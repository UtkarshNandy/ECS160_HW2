package com.ecs160;

import com.ecs160.persistence.Persistable;
import com.ecs160.persistence.PersistableId;

import java.util.List;

@Persistable
public class Post {
    @PersistableId
    private int postId;
    private String postContent;
    private List<Post> replies;
    private List<String> replyIds;

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

    public List<String> getReplyIds() {
        return replyIds;
    }


    public void setReplyIds(List<String> replyIds) {
        this.replyIds = replyIds;
    }


}

