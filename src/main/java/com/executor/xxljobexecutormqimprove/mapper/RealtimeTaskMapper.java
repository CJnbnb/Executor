package com.executor.xxljobexecutormqimprove.mapper;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RealtimeTaskMapper {
    boolean upsetTskInfo(CommonTaskEntity commonTaskEntity);
    // 其他方法可参考CommonTaskMapper按需添加
} 