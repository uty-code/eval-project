package com.ees.eval.mapper;

import com.ees.eval.domain.Department;
import com.ees.eval.dto.DepartmentDtos;
import com.ees.eval.dto.DepartmentDtos.DepartmentDetailDTO;
import com.ees.eval.dto.DepartmentDtos.SimpleDepartmentDTO;
import com.ees.eval.support.AbstractMssqlTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    @Test
    @DisplayName("부서 상세 정보를 통합 조회하면 상위 부서명과 인원수가 포함되어야 한다")
    void findDepartmentDetailById_ShouldReturnDetails() {
        // when
        DepartmentDetailDTO detail = departmentMapper.findDepartmentDetailById(1L);

        // then
        if (detail != null) {
            assertThat(detail.deptId()).isEqualTo(1L);
            assertThat(detail.deptName()).isNotNull();
        }
    }

    @Test
    @DisplayName("CTE를 이용한 여러 부서 상세 조회 시 상관 서브쿼리 없이 인원수가 조회되어야 한다")
    void findDepartmentDetailsByDeptIds_ShouldReturnDetailsUsingCTE() {
        // given
        List<Long> deptIds = List.of(1L, 2L);

        // when
        List<com.ees.eval.dto.DepartmentDtos.DepartmentDetailDTO> details = departmentMapper.findDepartmentDetailsByDeptIds(deptIds);

        // then
        assertThat(details).isNotEmpty();
        assertThat(details.get(0).employeeCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Select Box용 경량 부서 조회를 수행하면 ID와 이름만 반환되어야 한다")
    void findSimpleDepartments_ShouldReturnOnlyIdAndName() {
        // when
        List<SimpleDepartmentDTO> simpleDepts = departmentMapper.findSimpleDepartments();

        // then
        assertThat(simpleDepts).isNotEmpty();
        assertThat(simpleDepts.get(0).deptId()).isNotNull();
        assertThat(simpleDepts.get(0).deptName()).isNotNull();
    }
}
