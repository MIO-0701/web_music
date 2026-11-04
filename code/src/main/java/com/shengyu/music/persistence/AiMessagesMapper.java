package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 持久层 Mapper：连接 MySQL shengyu 库的 ai_messages：会话消息、解析条件与匹配歌曲结果。 提供该表的数据访问方法。 */
@Mapper
public interface AiMessagesMapper extends BaseMapper<AiMessages> {}
