import {DONE_STEPS_STORAGE_KEY, LAST_STEP, OPEN_STEP_STORAGE_KEY, state} from './state.js';

/** @typedef {import('./types.js').SeasonDraftReport} SeasonDraftReport */

// What the header says once a step is ticked off, for the two that had a to-do in there
/** @type {Record<number, string>} */
const DONE_OUTCOMES = {4: 'pushed and deployed', 7: 'posted'};

// getElementById hands back a plain HTMLElement, and that one has neither a value nor a disabled flag
/**
 * @param {string} elementId
 * @returns {HTMLInputElement}
 */
export function inputOf(elementId) {
    return /** @type {HTMLInputElement} */ (document.getElementById(elementId));
}

/**
 * @param {string} elementId
 * @returns {HTMLButtonElement}
 */
export function buttonOf(elementId) {
    return /** @type {HTMLButtonElement} */ (document.getElementById(elementId));
}

// The server sends one wrapper per container --> each [data-container] names where its html goes
/**
 * @param {string} html
 * @returns {DocumentFragment}
 */
export function fillSections(html) {
    const template = document.createElement('template');
    template.innerHTML = html;
    /** @type {NodeListOf<HTMLElement>} */ (template.content.querySelectorAll('[data-container]')).forEach(wrapper => {
        document.getElementById(wrapper.dataset.container).replaceChildren(...wrapper.childNodes);
    });

    return template.content;
}

// The checkmarks

/**
 * @param {number} step
 * @param {boolean} isDone
 */
export function setStepDone(step, isDone) {
    document.getElementById('step' + step + 'Body').closest('.accordion-item').classList.toggle('is-done', isDone);
}

/**
 * @param {number} step
 */
export function completeStep(step) {
    if (state.doneSteps.indexOf(step) === -1) state.doneSteps.push(step);
    sessionStorage.setItem(DONE_STEPS_STORAGE_KEY, JSON.stringify({
        seasonNumber: state.draftReport.seasonNumber,
        steps: state.doneSteps
    }));
    renderDoneSteps();

    if (step === LAST_STEP) {
        closeStep('step' + step + 'Body');
        return;
    }

    openStep('step' + (step + 1) + 'Body'); // opening the next one closes this one, they share the accordion
}

export function renderDoneSteps() {
    state.doneSteps.forEach(step => {
        setStepDone(step, true);
        if (DONE_OUTCOMES[step] !== undefined) setOutcome('step' + step + 'Outcome', DONE_OUTCOMES[step]);
    });
}

// Ticked off by hand, so they survive a reload - as long as it is still the same season
/**
 * @param {SeasonDraftReport} report
 */
export function restoreDoneSteps(report) {
    state.doneSteps = [];

    const stored = sessionStorage.getItem(DONE_STEPS_STORAGE_KEY);
    if (stored === null) return;

    try {
        const parsed = JSON.parse(stored);
        if (parsed.seasonNumber === report.seasonNumber) state.doneSteps = parsed.steps;
    } catch (error) {
        sessionStorage.removeItem(DONE_STEPS_STORAGE_KEY);
    }
}

export function forgetDoneSteps() {
    state.doneSteps = [];
    sessionStorage.removeItem(DONE_STEPS_STORAGE_KEY);

    for (let step = 1; step <= LAST_STEP; step++) setStepDone(step, false); // 0 is the token, that one is still there
}

// Odds and ends

/**
 * @param {string} elementId
 * @param {string} message
 */
export function showError(elementId, message) {
    const element = document.getElementById(elementId);
    element.textContent = message;
    element.classList.remove('d-none');
}

/**
 * @param {string} elementId
 */
export function hideError(elementId) {
    document.getElementById(elementId).classList.add('d-none');
}

/**
 * @param {string} elementId
 * @param {string} text
 */
export function setOutcome(elementId, text) {
    document.getElementById(elementId).textContent = text;
}

/**
 * @param {HTMLButtonElement} button
 * @param {string} label
 */
export function showSpinner(button, label) {
    const spinner = document.createElement('span');
    spinner.className = 'spinner-border spinner-border-sm';
    button.replaceChildren(spinner, ' ' + label);
}

/**
 * @param {string} bodyId
 */
export function openStep(bodyId) {
    bootstrap.Collapse.getOrCreateInstance(document.getElementById(bodyId)).show();
}

/**
 * @param {string} bodyId
 */
function closeStep(bodyId) {
    bootstrap.Collapse.getOrCreateInstance(document.getElementById(bodyId)).hide();
}

// Back where you were after a reload - waiting for the deploy with step 4 open shouldn't cost you the step
export function restoreOpenStep() {
    const bodyId = sessionStorage.getItem(OPEN_STEP_STORAGE_KEY);
    if (bodyId === null) return;
    if (document.getElementById(bodyId) === null) return; // stored by a build that numbered the steps differently

    openStep(bodyId);
}

/**
 * @param {HTMLElement} button
 */
export function flashCopied(button) {
    const label = button.textContent;
    button.textContent = 'Copied';
    setTimeout(() => {
        button.textContent = label;
    }, 1500);
}
