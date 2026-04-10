package com.ees.eval.security;

import com.ees.eval.domain.Employee;
import com.ees.eval.exception.EmployeeRetiredException;
import com.ees.eval.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security의 UserDetailsService 구현체입니다.
 * 데이터베이스에 등록된 사원(employees) 정보를 기반으로 인증 처리를 수행합니다.
 * 사원의 username으로 조회하고, employee_roles를 통해 권한을 로드합니다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeMapper employeeMapper;

    /**
     * Spring Security 인증 시 호출되는 메서드입니다.
     * username으로 사원 정보를 조회하고, 보유 권한 목록을 함께 로드하여
     * UserDetails 객체를 생성합니다.
     *
     * @param username 로그인 시 입력한 사용자 아이디
     * @return Spring Security 인증용 UserDetails 객체
     * @throws UsernameNotFoundException 해당 username의 사원이 존재하지 않을 경우 발생
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. employees 테이블에서 username으로 사원 조회 (삭제된 사원 포함)
        Employee employee = employeeMapper.findByUsernameIncludeDeleted(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "해당 아이디의 사원을 찾을 수 없습니다: " + username));

        // 1.1 퇴사자 체크
        if ("y".equalsIgnoreCase(employee.getIsDeleted())) {
            throw new EmployeeRetiredException("퇴사 처리된 계정입니다.");
        }

        // 2. employee_roles + roles 테이블을 JOIN하여 권한명 목록 조회
        List<String> roleNames = employeeMapper.findRoleNamesByEmpId(employee.getEmpId());

        // 3. Spring Security 표준 GrantedAuthority 리스트로 변환
        List<SimpleGrantedAuthority> authorities = roleNames.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        // 4. Spring Security의 User 객체로 감싸서 반환 (비밀번호 해시 포함)
        return new User(
                employee.getUsername(),
                employee.getPassword(),
                authorities);
    }
}
