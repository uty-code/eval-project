package com.ees.eval.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TestConnectionMapper {
    @Select("SELECT 1")
    int testConnection();
}
