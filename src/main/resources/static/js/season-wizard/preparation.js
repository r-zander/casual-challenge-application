import {state} from './state.js';
import {errorMessageOf, request} from './api.js';
import {buttonOf, hideError, inputOf, setOutcome, setStepDone, showError, showSpinner} from './steps.js';
import {agoText, daysBetween, endsText, formatDate, formatDateTime, isoDatePlusDays, lengthText, romanNumeral, todayInUtc} from './format.js';

/**
 * @typedef {import('./types.js').SeasonInfo} SeasonInfo
 * @typedef {import('./types.js').SeasonPreparationStatus} SeasonPreparationStatus
 */

const POLL_INTERVAL = 3 * 1000;
// casual-challenge.season.length-in-weeks and price-window-days, only used for the "leave it empty, and you get this" lines
const SEASON_LENGTH_IN_DAYS = 10 * 7;
const PRICE_WINDOW_DAYS = 70;

// SeasonPreparationStep: the five steps the status counts, stepNumber is 0 while it is still warming up
const PREPARATION_STEP_COUNT = 5;

/** @type {(() => Promise<void>) | null} */
let preparationDoneHandler = null;

/**
 * @param {() => Promise<void>} handler
 */
export function setPreparationDoneHandler(handler) {
    preparationDoneHandler = handler;
}

// Step 1 - preparing

/**
 * @returns {Promise<void>}
 */
export async function startPreparation() {
    hideError('prepareError');

    const parameters = new URLSearchParams();
    appendDateParameter(parameters, 'startDate');
    appendDateParameter(parameters, 'endDate');
    appendDateParameter(parameters, 'priceWindowStart');
    appendDateParameter(parameters, 'priceWindowEnd');

    const metaSource = metaSourceValue();
    if (metaSource !== 'mtggoldfish') parameters.set('metaSource', metaSource);

    /** @type {RequestInit} */
    const requestOptions = {method: 'POST'};
    if (metaSource === 'files') {
        const bansFile = inputOf('bansFile').files[0];
        const extendedBansFile = inputOf('extendedBansFile').files[0];
        if (bansFile === undefined || extendedBansFile === undefined) {
            showError('prepareError', 'The files source needs both bans.json and extended-bans.json.');
            return;
        }

        const formData = new FormData();
        formData.append('bans', bansFile);
        formData.append('extendedBans', extendedBansFile);
        requestOptions.body = formData;
    }

    const query = parameters.toString();
    const response = await request('./admin/v1/season/preparation' + (query === '' ? '' : '?' + query), requestOptions);
    if (!response.ok) {
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    renderPreparationStatus(await response.json());
    startPolling();
}

/**
 * @param {URLSearchParams} parameters
 * @param {string} elementId
 */
function appendDateParameter(parameters, elementId) {
    const value = inputOf(elementId).value;
    if (value !== '') parameters.set(elementId, value);
}

/**
 * @returns {Promise<void>}
 */
export async function cancelPreparation() {
    state.isStopping = true;
    const cancelButton = buttonOf('cancelButton');
    cancelButton.disabled = true;
    showSpinner(cancelButton, 'Stopping...');

    const response = await request('./admin/v1/season/preparation', {method: 'DELETE'});
    if (!response.ok) {
        state.isStopping = false;
        cancelButton.disabled = false;
        cancelButton.textContent = 'Give up on it';
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    await pollPreparation(); // waiting out the poll interval first looks like nothing happened
}

function startPolling() {
    if (state.pollTimer !== null) return;

    state.pollTimer = setInterval(pollPreparation, POLL_INTERVAL);
}

export function stopPolling() {
    if (state.pollTimer === null) return;

    clearInterval(state.pollTimer);
    state.pollTimer = null;
}

/**
 * @returns {Promise<void>}
 */
async function pollPreparation() {
    const response = await request('./admin/v1/season/preparation');
    if (!response.ok) {
        stopPolling();
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    const status = await response.json();
    renderPreparationStatus(status);
    if (status.state === 'RUNNING') return;

    stopPolling();
    if (status.state !== 'DONE') return;

    await preparationDoneHandler();
}

/**
 * @returns {string}
 */
export function metaSourceValue() {
    return /** @type {HTMLInputElement} */ (document.querySelector('input[name="metaSource"]:checked')).value;
}

/**
 * @param {SeasonPreparationStatus} status
 */
export function renderPreparationStatus(status) {
    state.preparationStatus = status;
    const isRunning = status.state === 'RUNNING';

    document.getElementById('preparationProgress').classList.toggle('d-none', status.state === 'IDLE');
    document.getElementById('cancelButton').classList.toggle('d-none', !isRunning);
    buttonOf('prepareButton').disabled = isRunning;
    document.getElementById('preparationClock').textContent = clockText(status);

    /** @type {NodeListOf<HTMLElement>} */ (document.querySelectorAll('#preparationSteps .preparation-step')).forEach(element => {
        const step = Number(element.dataset.step);
        element.classList.toggle('is-done', status.state === 'DONE' || step < status.stepNumber);
        element.classList.toggle('is-current', isRunning && step === status.stepNumber);
    });

    if (!isRunning && state.isStopping) {
        state.isStopping = false;
        buttonOf('cancelButton').disabled = false;
        buttonOf('cancelButton').textContent = 'Give up on it';
    }

    if (status.state === 'FAILED') {
        showError('prepareError', status.errorMessage !== null ? status.errorMessage : 'It gave up without saying why.');
    }

    if (isRunning) startPolling();
    setOutcome('step1Outcome', preparationOutcome(status));
}

// Nothing ran, or what ran has nothing to show for it anymore --> step 1 from the top
export function resetPreparation() {
    if (state.preparationStatus !== null && (state.preparationStatus.state === 'RUNNING' || state.preparationStatus.state === 'FAILED')) return;

    document.getElementById('preparationProgress').classList.add('d-none');
    document.getElementById('preparationClock').textContent = '';
    document.querySelectorAll('#preparationSteps .preparation-step').forEach(element => {
        element.classList.remove('is-done');
        element.classList.remove('is-current');
    });

    setOutcome('step1Outcome', 'nothing running');
    setStepDone(1, false);
}

/**
 * @param {SeasonPreparationStatus} status
 * @returns {string}
 */
function preparationOutcome(status) {
    switch (status.state) {
        case 'IDLE':
            return 'nothing running';
        case 'RUNNING':
            if (state.isStopping) return 'stopping, ' + status.step;

            // 0 is the warm-up line and the closing one, neither of them is one of the five steps
            return status.stepNumber === 0 ? status.step : 'step ' + status.stepNumber + ' of ' + PREPARATION_STEP_COUNT + ', ' + status.step;
        case 'DONE':
            return 'done';
        case 'FAILED':
            return 'failed';
        case 'CANCELLED':
            return 'cancelled';
        default:
            return status.state;
    }
}

/**
 * @param {SeasonPreparationStatus} status
 * @returns {string}
 */
function clockText(status) {
    if (status.startedAt === null || status.startedAt === undefined) return '';

    const startedAt = Date.parse(status.startedAt + 'Z'); // the API writes UTC without saying so
    const finishedAt = status.finishedAt !== null && status.finishedAt !== undefined
        ? Date.parse(status.finishedAt + 'Z')
        : Date.now();
    const seconds = Math.max(0, Math.round((finishedAt - startedAt) / 1000));
    const elapsed = seconds < 60 ? seconds + ' s' : Math.floor(seconds / 60) + ' min ' + (seconds % 60) + ' s';

    if (status.state !== 'RUNNING') return 'Took ' + elapsed + '.';
    if (state.isStopping) return 'Running for ' + elapsed + '. Stopping after the current step.';

    return 'Running for ' + elapsed + '.';
}

/**
 * @returns {Promise<SeasonInfo | null>}
 */
export async function loadCurrentSeason() {
    const response = await fetch('./legacy/season/current');
    state.currentSeason = response.ok ? await response.json() : null;
    renderCurrentSeason();
    renderPreparationDefaults();

    return state.currentSeason;
}

// Where we stand before anything is touched: the season that is live right now and how it is doing on time
function renderCurrentSeason() {
    document.getElementById('currentSeasonEmpty').classList.toggle('d-none', state.currentSeason !== null);
    document.getElementById('currentSeasonData').classList.toggle('d-none', state.currentSeason === null);
    if (state.currentSeason === null) return;

    const today = todayInUtc();
    const daysSinceStart = daysBetween(state.currentSeason.startDate, today);
    const daysUntilEnd = daysBetween(today, state.currentSeason.endDate);
    const isOverdue = daysUntilEnd < 0;

    document.getElementById('currentSeasonNumber').textContent = state.currentSeason.seasonNumber
        + ' (' + romanNumeral(state.currentSeason.seasonNumber) + ')';
    document.getElementById('currentSeasonStarted').textContent = formatDate(state.currentSeason.startDate) + ', ' + agoText(daysSinceStart);
    document.getElementById('currentSeasonScheduled').textContent = (isOverdue ? 'ended ' : 'ends ')
        + formatDate(state.currentSeason.endDate) + ', ' + endsText(daysUntilEnd);
    document.getElementById('currentSeasonScheduled').classList.toggle('text-warning', isOverdue);
    document.getElementById('currentSeasonRunning').textContent = lengthText(daysBetween(state.currentSeason.startDate, state.currentSeason.endDate) + 1)
        + ' planned, ' + lengthText(daysSinceStart + 1) + ' so far';
    document.getElementById('currentSeasonUpdated').textContent = 'updated ' + formatDateTime(state.currentSeason.updatedAt) + ' UTC';
}

// The dates SeasonPreparationService.withDefaults would pick. A date input ignores its placeholder --> below the field
export function renderPreparationDefaults() {
    const chosenStartDate = inputOf('startDate').value;
    const startDate = chosenStartDate !== '' ? chosenStartDate : todayInUtc();

    document.getElementById('startDateDefault').textContent = 'default: ' + formatDate(todayInUtc()) + ', today in UTC';
    document.getElementById('endDateDefault').textContent = endDateDefaultText(startDate);
    document.getElementById('priceWindowStartDefault').textContent = 'default:  '
        + formatDate(isoDatePlusDays(startDate, -PRICE_WINDOW_DAYS)) + ', ' + PRICE_WINDOW_DAYS + ' days before the start';
    document.getElementById('priceWindowEndDefault').textContent = 'default:  ' + formatDate(startDate)
        + ', the start date, end exclusive';
}

/**
 * @param {string} startDate
 * @returns {string}
 */
function endDateDefaultText(startDate) {
    if (state.currentSeason === null) return 'default: 10 weeks after the current season ends';

    const afterCurrentSeason = isoDatePlusDays(state.currentSeason.endDate, SEASON_LENGTH_IN_DAYS);
    if (afterCurrentSeason > startDate) {
        return 'default: ' + formatDate(afterCurrentSeason) + ', 10 weeks after season ' + state.currentSeason.seasonNumber + ' ends';
    }

    // Season ## ended ages ago --> the API counts the ten weeks from the new start instead
    return 'default: ' + formatDate(isoDatePlusDays(startDate, SEASON_LENGTH_IN_DAYS - 1)) + ', 10 weeks from the start, season '
        + state.currentSeason.seasonNumber + ' is long over';
}
