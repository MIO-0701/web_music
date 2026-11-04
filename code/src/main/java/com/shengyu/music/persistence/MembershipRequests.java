package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 membership_requests：用户会员申请及审核状态。 */
@TableName("membership_requests")
public class MembershipRequests {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("user_id")
    public Long userId;
    @TableField("status")
    public String status;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
    @TableField("reviewed_at")
    public java.time.LocalDateTime reviewedAt;
    @TableField("reviewer_id")
    public Long reviewerId;
}
