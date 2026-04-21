package com.ees.eval.mapper;

import com.ees.eval.domain.Department;
import com.ees.eval.support.AbstractMssqlTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>DepartmentMapper의 MSSQL 통합 테스트 클래스입니다.</p>
 * <p>AbstractMssqlTest를 상속받아 Testcontainers 기반의 실제 MSSQL 환경에서 실행됩니다.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("DepartmentMapper 통합 테스트 (MSSQL)")
class DepartmentMapperTest extends AbstractMssqlTest {

    @Autowired
    private DepartmentMapper departmentMapper;

    @Test
    @DisplayName("전체 부서 목록을 조회하면 초기 데이터가 포함되어야 한다")
    void findAll_ShouldReturnInitialDepartments() {
        // when
        List<Department> departments = departmentMapper.findAll();

        // then
        assertThat(departments).isNotEmpty();
        assertThat(departments.stream())
                .anyMatch(d -> d.getDeptName().contains("본부"));
    }

    @Test
    @DisplayName("신규 부서를 생성하고 조회할 수 있어야 한다")
    void insertAndFindById_ShouldWork() {
        // given
        Department dept = Department.builder()
                .deptName("테스트부서")
                .isActive("y")
                .build();
        dept.prePersist(); // 기초 필드 설정

        // when
        departmentMapper.insert(dept);
        Department found = departmentMapper.findById(dept.getDeptId()).orElse(null);

        // then
        assertThat(found).isNotNull();
        assertThat(found.getDeptName()).isEqualTo("테스트부서");
    }
}
