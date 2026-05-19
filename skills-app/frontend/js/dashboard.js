/**
 * dashboard.js — Skills matrix dashboard.
 * Loads the matrix from the API and renders a colour-coded HTML table.
 */

import { getSkillsMatrix, getSkills } from './api.js';

const LEVEL_CLASS = {
    NONE:    'cell-none',
    INITIAL: 'cell-initial',
    OK:      'cell-ok',
    GOOD:    'cell-good',
};

/** Render the colour legend. */
function renderLegend(container) {
    const items = [
        { cls: 'cell-good',    label: 'Good',    swatch: '#28A745' },
        { cls: 'cell-ok',      label: 'OK',      swatch: '#FFD700' },
        { cls: 'cell-initial', label: 'Initial', swatch: '#FF8C00' },
        { cls: 'cell-none',    label: 'None',    swatch: '#FFFFFF' },
    ];

    container.innerHTML = `
        <div class="legend">
            <span class="legend__title">Experience Level:</span>
            ${items.map(i => `
                <div class="legend__item">
                    <span class="legend__swatch" style="background:${i.swatch};border:1px solid #ccc"></span>
                    <span>${i.label}</span>
                </div>
            `).join('')}
        </div>
    `;
}

/**
 * Build a lookup: skill_id -> { code, label } from the employee skills in the matrix.
 * Also collects all unique skills present in the data.
 */
function collectSkills(matrixRows) {
    const skillMap = new Map(); // id -> skill object
    for (const row of matrixRows) {
        for (const es of row.skills) {
            if (!skillMap.has(es.skill_id)) {
                skillMap.set(es.skill_id, es.skill);
            }
        }
    }
    // Sort by category then name
    return [...skillMap.values()].sort((a, b) => {
        const catA = a.category?.name ?? '';
        const catB = b.category?.name ?? '';
        if (catA !== catB) return catA.localeCompare(catB);
        return a.name.localeCompare(b.name);
    });
}

/**
 * Render the matrix table into the given container element.
 */
function renderMatrix(container, matrixRows, skills) {
    if (matrixRows.length === 0) {
        container.innerHTML = '<p style="color:var(--text-muted)">No data to display.</p>';
        return;
    }

    // Header row
    const headerCells = skills.map(s => `<th title="${s.category?.name ?? ''}">${s.name}</th>`).join('');

    // Body rows — build a lookup per employee: skill_id -> experience_level
    const bodyRows = matrixRows.map(row => {
        const ratingMap = new Map(row.skills.map(es => [es.skill_id, es.experience_level]));
        const cells = skills.map(s => {
            const level = ratingMap.get(s.id);
            const cls   = level ? (LEVEL_CLASS[level.code] ?? 'cell-none') : 'cell-none';
            const label = level ? level.label : '—';
            return `<td class="${cls}" title="${s.name}: ${label}">${label}</td>`;
        }).join('');

        const emp = row.employee;
        const name = `${emp.first_name} ${emp.last_name}`;
        const link = `employee_detail.html?id=${emp.id}`;
        return `
            <tr>
                <td><a href="${link}">${name}</a></td>
                ${cells}
            </tr>
        `;
    }).join('');

    container.innerHTML = `
        <div class="matrix-wrapper">
            <table class="matrix-table">
                <thead>
                    <tr>
                        <th>Employee</th>
                        ${headerCells}
                    </tr>
                </thead>
                <tbody>
                    ${bodyRows}
                </tbody>
            </table>
        </div>
    `;
}

/** Main entry point. */
async function init() {
    const legendContainer  = document.getElementById('legend-container');
    const matrixContainer  = document.getElementById('matrix-container');

    // Render legend immediately
    if (legendContainer) renderLegend(legendContainer);

    // Loading state
    matrixContainer.innerHTML = '<div class="loading-container"><div class="spinner"></div></div>';

    try {
        const matrixRows = await getSkillsMatrix();
        const skills     = collectSkills(matrixRows);
        renderMatrix(matrixContainer, matrixRows, skills);
    } catch (err) {
        matrixContainer.innerHTML = `
            <div class="alert alert-error">
                Failed to load skills matrix: ${err.message}
            </div>
        `;
    }
}

document.addEventListener('DOMContentLoaded', init);
