package com.ees.eval.service;

import com.ees.eval.domain.Department;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.RoleMapper;
import com.ees.eval.service.impl.DepartmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 서비스 캐시 동작을 검증하는 테스트입니다.
 * 실제 DB 연동 대신 Mapper를 Mocking하여 캐시가 적용되었을 때
 * 실제 DB 조회가 생략되는지(Mapper 호출 횟수)를 확인합니다.
 */
@SpringBootTest
class DepartmentCacheTest {

    @Autowired
    private DepartmentService departmentService;

    @MockitoBean
    private DepartmentMapper departmentMapper;

    @MockitoBean
    private EmployeeMapper employeeMapper;

    @MockitoBean
    private RoleMapper roleMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        // 테스트 시작 전 캐시 초기화
        cacheManager.getCache("departments").clear();
        cacheManager.getCache("departments-simple").clear();
    }

    @Test
    @DisplayName("부서 전체 조회 시 캐시가 적용되어 Mapper 호출이 1번만 발생해야 함")
    void getAllDepartments_CacheTest() {
        // given
        Department mockDept = Department.builder().deptId(1L).deptName("테스트부서").build();
        when(departmentMapper.findAll()).thenReturn(List.of(mockDept));
        when(departmentMapper.findDepartmentDetailsByDeptIds(any())).thenReturn(Collections.emptyList());

        // when: 첫 번째 호출 (캐시 미존재 -> DB 조회)
        departmentService.getAllDepartments();
        
        // when: 두 번째 호출 (캐시 존재 -> DB 조회 생략)
        departmentService.getAllDepartments();

        // then: Mapper의 findAll은 2번 호출이 아닌 1번만 호출되어야 함
        verify(departmentMapper, times(1)).findAll();
    }

    @Test
    @DisplayName("부서 생성 시 기존 캐시가 삭제(Evict)되어 다시 DB 조회가 발생해야 함")
    void createDepartment_CacheEvictTest() {
        // given
        Department mockDept = Department.builder().deptId(1L).deptName("테스트부서").build();
        when(departmentMapper.findAll()).thenReturn(List.of(mockDept));
        when(departmentMapper.findDepartmentDetailsByDeptIds(any())).thenReturn(Collections.emptyList());
        when(departmentMapper.findById(any())).thenReturn(Optional.of(mockDept));
        com.ees.eval.dto.DepartmentDtos.DepartmentDetailDTO mockDetail = 
            new com.ees.eval.dto.DepartmentDtos.DepartmentDetailDTO(1L, null, null, "테스트부서", null, null, 0, "y", "n", 1, null, null);
        when(departmentMapper.findDepartmentDetailById(any())).thenReturn(mockDetail);

        // 1. 첫 조회로 캐시 생성
        departmentService.getAllDepartments();
        verify(departmentMapper, times(1)).findAll();

        // 2. 부서 생성 수행 (@CacheEvict 작동)
        DepartmentDTO newDept = DepartmentDTO.builder().deptName("신규부서").build();
        departmentService.createDepartment(newDept);

        // 3. 다시 조회 (캐시가 삭제되었으므로 DB 조회 재발생)
        departmentService.getAllDepartments();

        // then: findAll 호출 횟수가 총 2번이어야 함
        verify(departmentMapper, times(2)).findAll();
    }
}
