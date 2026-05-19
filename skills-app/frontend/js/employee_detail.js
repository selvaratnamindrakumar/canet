/**
 * employee_detail.js — Employee detail page logic.
 * Shows employee info, their skills grouped by category, and a form to update skills.
 */

import { getEmployee, getEmployeeSkills, getLevels, getSkills, updateEmployeeSkill, deleteEmployeeSkill } from './api.js';

const LEVEL_CLASS = {
    NONE:    'skill-none',
    INITIAL: 'skill-initial',
    OK:      'skill-ok',
    GOOD:    'skill-good',
};

/** Read a query parameter from the current URL. */
function getParam(name) {
    return new URLSearchParams(window.location.search).get(name);
}

/** Render the employee information header. */
function renderEmployeeInfo(container, emp) {
    const dept     = emp.department?.name ?? 'No department';
    const title    = emp.job_title ?? 'No title';
    const hireDate = emp.hire_date ? new Date(emp.hire_date).toLocaleDateString() : 'Unknown';
    const status   = emp.is_active
        ? '<span class="skill-badge skill-good">Active</span>'
        : '<span class="skill-badge skill-none">Inactive</span>';

    container.innerHTML = `
        <div class="employee-info-header">
            <div>
                <h2>${emp.first_name} ${emp.last_name} ${status}</h2>
                <div class="info-meta">
                    <span><strong>Code:</strong> ${emp.employee_code}</span>
                    <span><strong>Title:</strong> ${title}</span>
                    <span><strong>Department:</strong> ${dept}</span>
                    <span><strong>Hired:</strong> ${hireDate}</span>
                    <span><strong>Email:</strong> <a href="mailto:${emp.email}">${emp.email}</a></span>
                </div>
            </div>
        </div>
    `;
}

/** Group skills by category and render colour-coded badges. */
function renderSkills(container, employeeSkills) {
    if (employeeSkills.length === 0) {
        container.innerHTML = '<p style="color:var(--text-muted)">No skills recorded yet.</p>';
        return;
    }

    // Group by category name
    const groups = new Map();
    for (const es of employeeSkills) {
        const catName = es.skill.category?.name ?? 'Uncategorised';
        if (!groups.has(catName)) groups.set(catName, []);
        groups.get(catName).push(es);
    }

    let html = '';
    for (const [category, items] of [...groups.entries()].sort()) {
        const badges = items.map(es => {
            const cls   = LEVEL_CLASS[es.experience_level.code] ?? 'skill-none';
            const notes = es.notes ? ` title="${es.notes}"` : '';
            return `
                <span class="skill-badge ${cls}"${notes}>
                    ${es.skill.name}
                    <small style="opacity:.8;margin-left:.2rem">(${es.experience_level.label})</small>
                </span>
            `;
        }).join('');

        html += `
            <div class="category-section">
                <h3>${category}</h3>
                <div class="skills-list">${badges}</div>
            </div>
        `;
    }

    container.innerHTML = html;
}

/** Populate the skill update form selects. */
function populateForm(skillSelect, levelSelect, allSkills, levels) {
    skillSelect.innerHTML = allSkills.map(s =>
        `<option value="${s.id}">${s.name}${s.category ? ` (${s.category.name})` : ''}</option>`
    ).join('');

    levelSelect.innerHTML = levels.map(l =>
        `<option value="${l.id}">${l.label}</option>`
    ).join('');
}

/** Show a temporary alert message in the form area. */
function showAlert(container, message, type = 'success') {
    const div = document.createElement('div');
    div.className = `alert alert-${type}`;
    div.textContent = message;
    container.prepend(div);
    setTimeout(() => div.remove(), 3500);
}

/** Main entry point. */
async function init() {
    const id = getParam('id');
    if (!id) {
        document.body.innerHTML = '<div class="container"><div class="alert alert-error">No employee ID provided.</div></div>';
        return;
    }

    const infoContainer   = document.getElementById('employee-info');
    const skillsContainer = document.getElementById('skills-container');
    const formContainer   = document.getElementById('update-skill-form');

    // Show loading
    infoContainer.innerHTML   = '<div class="loading-container"><div class="spinner"></div></div>';
    skillsContainer.innerHTML = '';
    formContainer.innerHTML   = '';

    try {
        const [emp, employeeSkills, levels, allSkills] = await Promise.all([
            getEmployee(id),
            getEmployeeSkills(id),
            getLevels(),
            getSkills(),
        ]);

        // Render info header
        renderEmployeeInfo(infoContainer, emp);

        // Render skills
        const skillsHeader = document.createElement('h2');
        skillsHeader.style.cssText = 'font-size:1.1rem;font-weight:700;margin-bottom:1rem';
        skillsHeader.textContent = 'Skills';
        skillsContainer.appendChild(skillsHeader);

        const skillsBody = document.createElement('div');
        skillsContainer.appendChild(skillsBody);
        renderSkills(skillsBody, employeeSkills);

        // Render update form
        formContainer.innerHTML = `
            <div class="form-card" style="margin-top:1.5rem">
                <h3>Update Skill Rating</h3>
                <div class="form-row">
                    <div class="form-group">
                        <label for="skill-select">Skill</label>
                        <select id="skill-select"></select>
                    </div>
                    <div class="form-group">
                        <label for="level-select">Experience Level</label>
                        <select id="level-select"></select>
                    </div>
                    <div class="form-group" style="flex:2 1 240px">
                        <label for="notes-input">Notes (optional)</label>
                        <input type="text" id="notes-input" placeholder="e.g. Primary language, learning…" />
                    </div>
                    <div class="form-group" style="flex:0 0 auto">
                        <label>&nbsp;</label>
                        <button class="btn btn-primary" id="save-btn">Save</button>
                    </div>
                </div>
            </div>
        `;

        const skillSelect = document.getElementById('skill-select');
        const levelSelect = document.getElementById('level-select');
        const notesInput  = document.getElementById('notes-input');
        const saveBtn     = document.getElementById('save-btn');

        populateForm(skillSelect, levelSelect, allSkills, levels);

        saveBtn.addEventListener('click', async () => {
            saveBtn.disabled = true;
            try {
                await updateEmployeeSkill(id, {
                    skill_id:           parseInt(skillSelect.value),
                    experience_level_id: parseInt(levelSelect.value),
                    notes:              notesInput.value.trim() || null,
                });

                // Refresh skills display
                const refreshed = await getEmployeeSkills(id);
                renderSkills(skillsBody, refreshed);
                showAlert(formContainer, 'Skill rating saved successfully.', 'success');
                notesInput.value = '';
            } catch (err) {
                showAlert(formContainer, `Error: ${err.message}`, 'error');
            } finally {
                saveBtn.disabled = false;
            }
        });

    } catch (err) {
        infoContainer.innerHTML = `
            <div class="alert alert-error">Failed to load employee: ${err.message}</div>
        `;
    }
}

document.addEventListener('DOMContentLoaded', init);
