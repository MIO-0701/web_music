package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("recommendation_experiments")
public class RecommendationExperiments {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("algorithm")
    public String algorithm;
    @TableField("dataset")
    public String dataset;
    @TableField("precision_at_10")
    public Double precisionAt_10;
    @TableField("recall_at_10")
    public Double recallAt_10;
    @TableField("coverage")
    public Double coverage;
    @TableField("evaluated_users")
    public Integer evaluatedUsers;
    @TableField("details")
    public String details;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
