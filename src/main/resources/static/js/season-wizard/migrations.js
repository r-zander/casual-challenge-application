import {SQL_PARTS, state} from './state.js';
import {errorMessageOf, request} from './api.js';
import {hideError, setOutcome, setStepDone, showError} from './steps.js';

/** @typedef {import('./types.js').SeasonDraftReport} SeasonDraftReport */

// Step 4 - the migrations

/**
 * @param {SeasonDraftReport} report
 */
export function renderDownloads(report) {
    document.getElementById('downloadWarning').classList.toggle('d-none', report.committedAt !== null);
    setOutcome('step4Outcome', '');
    renderPullRequest(report.pullRequestUrl);
    markGitChecklist();
}

/**
 * @param {string | null} pullRequestUrl
 */
function renderPullRequest(pullRequestUrl) {
    const isOpened = hasPullRequest();
    document.getElementById('pullRequestResult').classList.toggle('d-none', !isOpened);
    if (!isOpened) return;

    const number = pullRequestUrl.substring(pullRequestUrl.lastIndexOf('/') + 1);
    /** @type {HTMLAnchorElement} */ (document.getElementById('pullRequestLink')).href = pullRequestUrl;
    document.getElementById('pullRequestNumber').textContent = number;
    setOutcome('step4Outcome', 'pull request #' + number + ' opened');
    setStepDone(4, true);
}

/**
 * @returns {boolean}
 */
function hasPullRequest() {
    return state.draftReport !== null && state.draftReport.pullRequestUrl !== null && state.draftReport.pullRequestUrl !== undefined;
}

// A pull request does 1 to 4 on its own, without one the downloads are the only entry the page can tick
function markGitChecklist() {
    /** @type {NodeListOf<HTMLElement>} */ (document.querySelectorAll('#gitChecklist .checklist-step')).forEach(element => {
        const step = Number(element.dataset.checklistStep);
        const isDone = hasPullRequest() ? step <= 4 : step === 1 && state.downloadedParts.length === SQL_PARTS.length;
        element.classList.toggle('is-done', isDone);
    });
}

/**
 * @returns {Promise<void>}
 */
export async function downloadAllSql() {
    for (let index = 0; index < SQL_PARTS.length; index++) {
        const wasDownloaded = await downloadSql(SQL_PARTS[index]);
        if (!wasDownloaded) return; // the other two would fail the same way
    }
}

/**
 * @param {string} part
 * @returns {Promise<boolean>}
 */
export async function downloadSql(part) {
    hideError('downloadError');

    const response = await request('./admin/v1/season/draft/sql/' + part);
    if (!response.ok) {
        showError('downloadError', await errorMessageOf(response));
        return false;
    }

    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = fileNameOf(response, part);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);

    if (state.downloadedParts.indexOf(part) === -1) state.downloadedParts.push(part);
    if (!hasPullRequest()) setOutcome('step4Outcome', state.downloadedParts.length + ' of ' + SQL_PARTS.length + ' downloaded');
    markGitChecklist();

    return true;
}

// Same rule as SeasonDraftService.sqlFileName, for when the header doesn't survive the trip
/**
 * @param {Response} response
 * @param {string} part
 * @returns {string}
 */
function fileNameOf(response, part) {
    const disposition = response.headers.get('Content-Disposition');
    if (disposition !== null) {
        const match = disposition.match(/filename="([^"]+)"/);
        if (match !== null) return match[1];
    }

    const writtenAt = state.draftReport.committedAt !== null ? state.draftReport.committedAt : state.draftReport.preparedAt;
    const prefix = writtenAt.substring(0, 16).replace(/[-:]/g, '').replace('T', '_');
    switch (part) {
        case '00_add_season':
            return prefix + '_' + part + '_' + state.draftReport.seasonNumber + '.sql';
        case '02_insert_card_season_data':
            return prefix + '_' + part + '_for_season_' + state.draftReport.seasonNumber + '.sql';
        default:
            return prefix + '_' + part + '.sql';
    }
}
