package com.ees.eval;

import com.ees.eval.mapper.TestConnectionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DatabaseConnectionTest {

    @Autowired
    private TestConnectionMapper testConnectionMapper;

    @Test
    public void testDatabaseConnection() {
        int result = testConnectionMapper.testConnection();
        assertThat(result).isEqualTo(1);
    }
}
