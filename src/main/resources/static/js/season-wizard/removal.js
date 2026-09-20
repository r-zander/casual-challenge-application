import {COMMIT_RESULT_STORAGE_KEY, state} from './state.js';
import {errorMessageOf, request} from './api.js';
import {buttonOf, fillSections, hideError, inputOf, showError, showSpinner} from './steps.js';
import {formatDate, formatNumber} from './format.js';
import {loadCurrentSeason} from './preparation.js';

/**
 * @typedef {import('./types.js').RemovedSeason} RemovedSeason
 * @typedef {import('./types.js').SeasonRemovalPreview} SeasonRemovalPreview
 */

// Removing a season

/**
 * @returns {Promise<void>}
 */
export async function loadRemovalPreview() {
    hideError('removalError');
    document.getElementById('removalPreview').classList.add('d-none');
    document.getElementById('removalConfirm').classList.add('d-none');
    document.getElementById('removalResult').classList.add('d-none');
    state.removalPreview = null;

    const seasonNumber = inputOf('removalSeasonInput').value.trim();
    if (seasonNumber === '') {
        showError('removalError', 'Which season?');
        return;
    }

    const response = await request('./admin/v1/season/' + encodeURIComponent(seasonNumber) + '/removal');
    if (!response.ok) {
        showError('removalError', await errorMessageOf(response));
        return;
    }

    const sectionsResponse = await request('./admin/season-wizard/season/' + encodeURIComponent(seasonNumber) + '/removal');
    if (!sectionsResponse.ok) {
        showError('removalError', await errorMessageOf(sectionsResponse)); // nothing on screen to read --> nothing to confirm either
        return;
    }

    const preview = await response.json();
    const sectionsBody = await sectionsResponse.text();

    state.removalPreview = preview;
    fillSections(sectionsBody);
    renderRemovalPreview(preview);
}

/**
 * @param {SeasonRemovalPreview} preview
 */
function renderRemovalPreview(preview) {
    document.getElementById('removalPreview').classList.remove('d-none');
    document.getElementById('removalTokenBlock').classList.toggle('d-none', preview.pullRequestUrl === null);
    inputOf('removalConfirmInput').value = '';
    document.getElementById('removalConfirm').classList.toggle('d-none', !preview.removable);
    updateRemovalButton();
}

export function updateRemovalButton() {
    const isConfirmed = state.removalPreview !== null
        && inputOf('removalConfirmInput').value.trim() === String(state.removalPreview.seasonNumber);

    buttonOf('removalButton').disabled = !isConfirmed;
}

/**
 * @returns {Promise<void>}
 */
export async function removeSeason() {
    if (!window.confirm('Remove season ' + state.removalPreview.seasonNumber + '? Season ' + state.removalPreview.previousSeasonNumber + ' is the current one afterwards.')) return;

    hideError('removalError');
    const removalButton = buttonOf('removalButton');
    removalButton.disabled = true;
    showSpinner(removalButton, 'Removing...');

    const githubToken = inputOf('removalGithubToken').value.trim();
    const response = await request('./admin/v1/season/' + state.removalPreview.seasonNumber + '/removal', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({githubToken: githubToken === '' ? null : githubToken})
    });
    removalButton.textContent = 'Remove the season';
    if (!response.ok) {
        showError('removalError', await errorMessageOf(response));
        updateRemovalButton();
        return;
    }

    sessionStorage.removeItem(COMMIT_RESULT_STORAGE_KEY); // that commit is undone
    renderRemoved(await response.json());
    await loadCurrentSeason(); // the season before it is the current one again
}

/**
 * @param {RemovedSeason} removed
 */
function renderRemoved(removed) {
    document.getElementById('removedSeason').textContent = String(removed.seasonNumber);
    document.getElementById('removedPreviousSeason').textContent = String(removed.previousSeasonNumber);
    document.getElementById('removedPreviousSeasonEndDate').textContent = formatDate(removed.previousSeasonEndDate);
    document.getElementById('removedCardSeasonData').textContent = formatNumber(removed.counts.cardSeasonDataRows) + ' card season data rows gone';
    document.getElementById('removedCards').textContent = formatNumber(removed.counts.deletedCards) + ' cards gone';
    document.getElementById('removedRenames').textContent = removed.counts.undoneRenames + ' renames and '
        + removed.counts.undoneRemaps + ' oracle ids back where they were';
    document.getElementById('removedPullRequest').textContent = removed.closedPullRequestUrl !== null
        ? 'branch deleted, ' + removed.closedPullRequestUrl + ' closed with it'
        : '';
    document.getElementById('removedPullRequest').classList.toggle('d-none', removed.closedPullRequestUrl === null);
    document.getElementById('removedArchive').classList.toggle('d-none', !removed.archiveDeleted);

    document.getElementById('removalPreview').classList.add('d-none');
    document.getElementById('removalConfirm').classList.add('d-none');
    inputOf('removalGithubToken').value = '';
    document.getElementById('removalResult').classList.remove('d-none');
    state.removalPreview = null;
}
