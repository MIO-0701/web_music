package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 持久层 Mapper：连接 MySQL shengyu 库的 ai_conversations：用户与 AI 的会话标题及更新时间。 提供该表的数据访问方法。 */
@Mapper
public interface AiConversationsMapper extends BaseMapper<AiConversations> {}
