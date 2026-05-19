/**
 * employees.js — Employee list page logic.
 * Fetches all employees and renders them as filterable cards.
 */

import { getEmployees } from './api.js';

let allEmployees = [];

/** Render employee cards into the container. */
function renderEmployees(container, employees) {
    if (employees.length === 0) {
        container.innerHTML = '<p style="color:var(--text-muted)">No employees found.</p>';
        return;
    }

    container.innerHTML = `
        <div class="employee-grid">
            ${employees.map(emp => {
                const name   = `${emp.first_name} ${emp.last_name}`;
                const dept   = emp.department?.name ?? 'No department';
                const title  = emp.job_title ?? 'No title';
                const code   = emp.employee_code;
                const status = emp.is_active
                    ? '<span class="skill-badge skill-good">Active</span>'
                    : '<span class="skill-badge skill-none">Inactive</span>';

                return `
                    <a href="employee_detail.html?id=${emp.id}" class="employee-card">
                        <div class="card">
                            <div class="card__title">${name}</div>
                            <div class="card__subtitle">${title}</div>
                            <div class="card__meta">
                                <div>${dept}</div>
                                <div style="margin-top:.35rem">
                                    <code style="font-size:.8rem;color:var(--text-muted)">${code}</code>
                                    &nbsp;${status}
                                </div>
                            </div>
                        </div>
                    </a>
                `;
            }).join('')}
        </div>
    `;
}

/** Filter employees by the search term against name, title, and department. */
function filterEmployees(query) {
    const q = query.toLowerCase().trim();
    if (!q) return allEmployees;
    return allEmployees.filter(emp => {
        const name  = `${emp.first_name} ${emp.last_name}`.toLowerCase();
        const title = (emp.job_title ?? '').toLowerCase();
        const dept  = (emp.department?.name ?? '').toLowerCase();
        const code  = emp.employee_code.toLowerCase();
        return name.includes(q) || title.includes(q) || dept.includes(q) || code.includes(q);
    });
}

/** Main entry point. */
async function init() {
    const container   = document.getElementById('employees-container');
    const searchInput = document.getElementById('search-input');
    const countLabel  = document.getElementById('employee-count');

    container.innerHTML = '<div class="loading-container"><div class="spinner"></div></div>';

    try {
        allEmployees = await getEmployees();

        const update = (query = '') => {
            const filtered = filterEmployees(query);
            if (countLabel) countLabel.textContent = `${filtered.length} employee${filtered.length !== 1 ? 's' : ''}`;
            renderEmployees(container, filtered);
        };

        update();

        if (searchInput) {
            searchInput.addEventListener('input', e => update(e.target.value));
        }
    } catch (err) {
        container.innerHTML = `
            <div class="alert alert-error">
                Failed to load employees: ${err.message}
            </div>
        `;
    }
}

document.addEventListener('DOMContentLoaded', init);
