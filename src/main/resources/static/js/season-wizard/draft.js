import {COMMIT_RESULT_STORAGE_KEY, state} from './state.js';
import {errorMessageOf, request} from './api.js';
import {fillSections, forgetDoneSteps, hideError, inputOf, openStep, renderDoneSteps, restoreDoneSteps, setOutcome, setStepDone, showError} from './steps.js';
import {byLine, cardCountOf, formatDate, formatDateTime, formatNumber} from './format.js';
import {resetPreparation} from './preparation.js';
import {renderAnnouncement, restoreCommitResult, runLiveChecks, updateCommitButton} from './commit.js';
import {renderDownloads} from './migrations.js';

/** @typedef {import('./types.js').SeasonDraftReport} SeasonDraftReport */

// Everything the server renders for step 2, 5 and 6
const REPORT_CONTAINERS = ['draftHeader', 'sanityChecks', 'commitBlockedReason', 'reportSections',
    'seasonHistoryTable', 'scryfallDecks'];

// Step 2 - the draft

/**
 * @returns {Promise<void>}
 */
export async function loadDraft() {
    const response = await request('./admin/v1/season/draft');
    if (response.status === 404) {
        state.draftReport = null;
        renderNoDraft();
        return;
    }
    if (!response.ok) {
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    // The json is the state, the fragment only the view --> both bodies are in hand before anything on the page moves
    const sectionsResponse = await request('./admin/season-wizard/draft');
    const report = await response.json();
    const sectionsBody = sectionsResponse.ok ? await sectionsResponse.text() : await errorMessageOf(sectionsResponse);

    state.draftReport = withEmptyLists(report);
    state.downloadedParts = [];
    restoreDoneSteps(state.draftReport);

    if (sectionsResponse.ok) {
        hideError('draftError');
        const sanityChecks = /** @type {HTMLElement} */ (fillSections(sectionsBody).querySelector('[data-container="sanityChecks"]'));
        state.hasBlockingSanityFailure = sanityChecks === null || sanityChecks.dataset.commitBlocked !== 'false'; // no verdict --> no commit without the checkbox
    } else {
        showError('draftError', sectionsBody);
        renderMissingReport();
        state.hasBlockingSanityFailure = true;
    }

    renderDraft(state.draftReport);
    if (state.draftReport.committedAt !== null) {
        restoreCommitResult(state.draftReport);
        await runLiveChecks();
    }
}

// The draft is there, its report is not --> the one of the draft before it must not stay on screen next to it
function renderMissingReport() {
    REPORT_CONTAINERS.forEach(container => document.getElementById(container).replaceChildren());
    document.getElementById('commitBlockedReason').textContent = 'The report didn\'t load, so nobody checked the draft.';
}

// Reports written by older builds leave some of the lists out entirely --> don't let one null take the page down
/**
 * @param {SeasonDraftReport} report
 * @returns {SeasonDraftReport}
 */
function withEmptyLists(report) {
    if (report.setsReleased === null || report.setsReleased === undefined) report.setsReleased = [];
    if (report.scryfallDecks === null || report.scryfallDecks === undefined) {
        report.scryfallDecks = {newBans: '', unbans: '', currentBans: ''};
    }

    return report;
}

function renderNoDraft() {
    ['step2', 'step4', 'step5', 'step6'].forEach(step => {
        document.getElementById(step + 'Empty').classList.remove('d-none');
        document.getElementById(step + 'Data').classList.add('d-none');
    });
    document.getElementById('step7Empty').classList.remove('d-none');
    document.getElementById('step7Data').classList.add('d-none');
    document.getElementById('commitBlocked').classList.add('d-none');
    document.getElementById('commitResult').classList.add('d-none');
    document.getElementById('liveChecks').classList.add('d-none');

    ['step2Outcome', 'step3Outcome', 'step4Outcome', 'step5Outcome', 'step6Outcome', 'step7Outcome']
        .forEach(outcome => setOutcome(outcome, ''));
    forgetDoneSteps();
    sessionStorage.removeItem(COMMIT_RESULT_STORAGE_KEY);
    resetPreparation(); // the run that led here is gone with the draft
    state.hasBlockingSanityFailure = false;
    updateCommitButton();
}

/**
 * @param {SeasonDraftReport} report
 */
function renderDraft(report) {
    inputOf('commitAnyway').checked = false;
    document.getElementById('discardButton').classList.toggle('d-none', report.committedAt !== null); // a committed draft stays, it is the record of that season start
    ['step2', 'step4', 'step5', 'step6'].forEach(step => {
        document.getElementById(step + 'Empty').classList.add('d-none');
        document.getElementById(step + 'Data').classList.remove('d-none');
    });

    document.getElementById('commitBlocked').classList.toggle('d-none', !state.hasBlockingSanityFailure);
    renderDownloads(report);
    updateCommitButton();

    setStepDone(1, true);
    setStepDone(3, report.committedAt !== null);

    setOutcome('step2Outcome', 'season ' + report.seasonNumber + ', ' + formatNumber(report.counts.cards) + ' cards, '
        + report.counts.newBans + ' new bans, ' + report.counts.unbans + ' unbans');
    setOutcome('step5Outcome', 'season ' + report.seasonNumber + ', ' + formatDate(report.startDate) + ' - ' + formatDate(report.endDate));
    setOutcome('step6Outcome', [report.scryfallDecks.newBans, report.scryfallDecks.unbans, report.scryfallDecks.currentBans]
        .map(cardCountOf).join(' / '));

    if (report.committedAt === null) {
        setOutcome('step3Outcome', 'not committed');
    } else {
        setOutcome('step3Outcome', 'committed ' + formatDateTime(report.committedAt) + byLine(report.committedBy));
        renderAnnouncement(report, report.setsReleased); // committed before the page was opened --> the facts come out of the report
    }

    renderDoneSteps(); // after the outcomes above, two of them get overwritten
}

/**
 * @returns {Promise<void>}
 */
export async function discardDraft() {
    if (!window.confirm('Throw the draft for season ' + state.draftReport.seasonNumber + ' away?')) return;

    const response = await request('./admin/v1/season/draft', {method: 'DELETE'});
    if (!response.ok) {
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    resetPreparation();
    await loadDraft(); // a DELETE hands back the last committed draft, if there is one
    openStep('step1Body');
}

// Step 5 - the Season History row

// Google Docs drops the html into the selected cells, everything else gets the tab separated version
/**
 * @param {string} elementId
 * @returns {Promise<void>}
 */
export async function copySeasonHistoryRow(elementId) {
    const row = document.getElementById(elementId);
    /** @type {string[]} */
    const values = [];
    row.querySelectorAll('td').forEach(cell => values.push(cell.innerText.replace(/\n/g, ', ')));
    const text = values.join('\t');

    if (window.ClipboardItem === undefined) { // plain http, or a browser that never heard of it
        await navigator.clipboard.writeText(text);
        return;
    }

    await navigator.clipboard.write([new ClipboardItem({
        'text/html': new Blob(['<table>' + row.outerHTML + '</table>'], {type: 'text/html'}),
        'text/plain': new Blob([text], {type: 'text/plain'})
    })]);
}
