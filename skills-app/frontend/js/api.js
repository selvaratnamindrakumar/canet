/**
 * api.js — Centralised API client for the Employee Skills application.
 * All functions return parsed JSON or throw an Error on non-2xx responses.
 */

export const API_BASE = 'http://localhost:8000';

/**
 * Generic fetch wrapper — throws Error if response is not ok.
 */
async function request(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        headers: { 'Content-Type': 'application/json', ...options.headers },
        ...options,
    });

    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            const body = await response.json();
            message = body.detail || JSON.stringify(body) || message;
        } catch (_) { /* ignore parse errors */ }
        throw new Error(message);
    }

    // 204 No Content has no body
    if (response.status === 204) return null;
    return response.json();
}

/* -----------------------------------------------------------------------
   Employees
   --------------------------------------------------------------------- */

/** List all active employees (with department). */
export async function getEmployees() {
    return request('/employees/');
}

/** Get a single employee by ID. */
export async function getEmployee(id) {
    return request(`/employees/${id}`);
}

/** Get all skill ratings for an employee. */
export async function getEmployeeSkills(id) {
    return request(`/employees/${id}/skills`);
}

/**
 * Add or update a skill rating for an employee (upsert).
 * @param {number} employeeId
 * @param {{ skill_id: number, experience_level_id: number, notes?: string }} payload
 */
export async function updateEmployeeSkill(employeeId, payload) {
    return request(`/employees/${employeeId}/skills`, {
        method: 'POST',
        body: JSON.stringify(payload),
    });
}

/**
 * Remove a skill rating from an employee.
 * @param {number} employeeId
 * @param {number} skillId
 */
export async function deleteEmployeeSkill(employeeId, skillId) {
    return request(`/employees/${employeeId}/skills/${skillId}`, {
        method: 'DELETE',
    });
}

/* -----------------------------------------------------------------------
   Skills
   --------------------------------------------------------------------- */

/**
 * List all skills, optionally filtered by category.
 * @param {number|null} categoryId
 */
export async function getSkills(categoryId = null) {
    const qs = categoryId != null ? `?category_id=${categoryId}` : '';
    return request(`/skills/${qs}`);
}

/** List all skill categories. */
export async function getCategories() {
    return request('/skills/categories');
}

/** List all experience levels. */
export async function getLevels() {
    return request('/skills/levels');
}

/**
 * Get the full skills matrix.
 * @param {{ department_id?: number, skill_ids?: number[], min_level_id?: number }} params
 */
export async function getSkillsMatrix(params = {}) {
    const qs = new URLSearchParams();
    if (params.department_id != null)  qs.set('department_id', params.department_id);
    if (params.min_level_id  != null)  qs.set('min_level_id',  params.min_level_id);
    if (Array.isArray(params.skill_ids) && params.skill_ids.length > 0) {
        qs.set('skill_ids', params.skill_ids.join(','));
    }
    const query = qs.toString() ? `?${qs}` : '';
    return request(`/skills/matrix${query}`);
}

/**
 * Search employees by skills and minimum level.
 * @param {{ skill_ids: number[], min_level_id: number }} filter
 */
export async function searchEmployees(filter) {
    return request('/skills/search', {
        method: 'POST',
        body: JSON.stringify(filter),
    });
}
