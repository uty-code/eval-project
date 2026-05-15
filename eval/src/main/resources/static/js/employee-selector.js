/**
 * 공통 사원 검색 모달 호출 유틸리티
 * 
 * @param {Function} callback - 사원 선택 시 호출될 콜백 함수. 
 *                              인자로 employee 객체(id, name, dept, pos, color)를 전달받습니다.
 */
window.openEmployeeSelector = function(callback) {
    // 1. HTMX를 이용해 사원 검색 모달 HTML을 비동기로 가져옵니다.
    htmx.ajax('GET', '/employees/selector', {
        target: 'body',
        swap: 'beforeend'
    }).then(() => {
        const modalEl = document.getElementById('employeeSelectorModal');
        if (modalEl) {
            // 2. Bootstrap 모달 인스턴스화
            const modal = new bootstrap.Modal(modalEl, {
                backdrop: 'static',
                focus: true,
                keyboard: false // ESC 수동 제어를 위해 내장 키보드 옵션 비활성화
            });
            
            // 3. 모달 화면에 표시
            modal.show();
            
            // 4. 모달 간섭 방지를 위한 ESC 수동 처리 (다중 모달 대응)
            const handleEsc = (e) => {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    modal.hide();
                }
            };
            window.addEventListener('keydown', handleEsc, { capture: true });

            // 5. 모달 표시 후 오토 포커스
            modalEl.addEventListener('shown.bs.modal', function() {
                const searchInput = modalEl.querySelector('#selector-search-input');
                if (searchInput) searchInput.focus();
            });

            // 6. 모달 종료 시 이벤트 정리 및 DOM 요소 완전 삭제 (메모리 누수 방지)
            modalEl.addEventListener('hidden.bs.modal', function() {
                window.removeEventListener('keydown', handleEsc, { capture: true });
                modalEl.remove(); // DOM에서 삭제
                
                // 기존 모달(예: 매핑 모달 등)이 있다면 해당 요소로 포커스 반환
                const activeModal = document.querySelector('.modal.show');
                if (activeModal) activeModal.focus();
            });
            
            // 7. employee-selector.html 내부에서 호출되는 글로벌 콜백 연결
            window.onEmployeeSelected = function(employee) {
                if (callback && typeof callback === 'function') {
                    callback(employee);
                }
                // 콜백 1회용으로 소비 후 null 처리 (선택사항)
                window.onEmployeeSelected = null;
            };
        }
    });
};
