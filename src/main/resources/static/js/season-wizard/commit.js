import {COMMIT_RESULT_STORAGE_KEY, state} from './state.js';
import {errorMessageOf, request} from './api.js';
import {buttonOf, completeStep, hideError, inputOf, setOutcome, showError} from './steps.js';
import {formatDate, formatDateTime, formatGermanDate, formatGermanNumber, formatNumber, romanNumeral} from './format.js';
import {loadCurrentSeason} from './preparation.js';

/**
 * @typedef {import('./types.js').ChildSet} ChildSet
 * @typedef {import('./types.js').CommittedSeason} CommittedSeason
 * @typedef {import('./types.js').MtgSet} MtgSet
 * @typedef {import('./types.js').SeasonDraftReport} SeasonDraftReport
 */

// Step 3 - committing

export function updateCommitButton() {
    const hasDraft = state.draftReport !== null;
    const isCommitted = hasDraft && state.draftReport.committedAt !== null;
    const isOverridden = inputOf('commitAnyway').checked;

    buttonOf('commitButton').disabled = !hasDraft || isCommitted || (state.hasBlockingSanityFailure && !isOverridden);
    buttonOf('commitButton').textContent = isCommitted ? 'Already committed' : 'Commit the season';
}

/**
 * @returns {Promise<boolean>}
 */
export async function commitSeason() {
    const githubToken = inputOf('githubTokenInput').value.trim();
    const question = githubToken === ''
        ? 'Commit season ' + state.draftReport.seasonNumber + '? /v1/cards waits a second or two while it goes in.'
        : 'Commit season ' + state.draftReport.seasonNumber + ' and open a pull request with the migrations?';
    if (!window.confirm(question)) return false;

    hideError('commitError');
    buttonOf('commitButton').disabled = true;
    document.getElementById('reloadCacheButton').classList.add('d-none');

    const response = await request('./admin/v1/season/commit', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            githubToken: githubToken === '' ? null : githubToken,
            commitAnyway: inputOf('commitAnyway').checked // the server checks the draft as well, the checkbox unlocks both ends
        })
    });
    if (!response.ok) {
        const message = await errorMessageOf(response);
        showError('commitError', response.status === 409 ? message + ' Nothing was written to the database.' : message);
        // The one failure that leaves a committed season behind names its own fix
        // TODO grepping the message for it is a hack, the commit should answer with something structured
        document.getElementById('reloadCacheButton').classList.toggle('d-none', message.indexOf('cards/reload') === -1);
        updateCommitButton();
        return false;
    }

    inputOf('githubTokenInput').value = ''; // it did its job, no reason to keep it around
    const committed = await response.json();
    sessionStorage.setItem(COMMIT_RESULT_STORAGE_KEY, JSON.stringify({
        seasonNumber: committed.seasonNumber,
        committed: committed
    }));

    return true;
}

// The counts and the pull request number are in that one answer and nowhere else - no GET replays them
/**
 * @param {SeasonDraftReport} report
 */
export function restoreCommitResult(report) {
    const stored = sessionStorage.getItem(COMMIT_RESULT_STORAGE_KEY);
    if (stored === null) return;

    let committed = null;
    try {
        const parsed = JSON.parse(stored);
        if (parsed.seasonNumber === report.seasonNumber) committed = parsed.committed;
    } catch (error) {
        sessionStorage.removeItem(COMMIT_RESULT_STORAGE_KEY);
    }
    if (committed === null) return; // committed from somewhere else --> the report is all we have

    renderCommitted(committed);
}

/**
 * @param {CommittedSeason} committed
 */
function renderCommitted(committed) {
    const isOpened = committed.pullRequest !== null && committed.pullRequest !== undefined;

    document.getElementById('committedAlertSeason').textContent = String(committed.seasonNumber);
    document.getElementById('committedSeason').textContent = committed.seasonNumber + ' (' + committed.romanSeasonNumber + ')';
    document.getElementById('committedStartDate').textContent = formatDate(committed.startDate);
    document.getElementById('committedFinalsFriday').textContent = formatDate(committed.finalsFriday);
    document.getElementById('committedEndDate').textContent = formatDate(committed.endDate);
    document.getElementById('committedNextSeasonStart').textContent = formatDate(committed.nextSeasonStart);
    document.getElementById('committedInsertedCards').textContent = formatNumber(committed.counts.insertedCards);
    document.getElementById('committedCardSeasonData').textContent = formatNumber(committed.counts.upsertedCardSeasonData);
    document.getElementById('committedRemappedCards').textContent = formatNumber(committed.counts.remappedCards);
    document.getElementById('committedUpdatedCardNames').textContent = formatNumber(committed.counts.updatedCardNames);
    document.getElementById('committedPullRequest').textContent = isOpened ? committed.pullRequest.url : '';
    document.getElementById('committedPullRequestRow').classList.toggle('d-none', !isOpened);
    document.getElementById('commitResult').classList.remove('d-none');

    renderAnnouncement(committed, committed.newSets);
    document.getElementById('downloadWarning').classList.add('d-none');
}

/**
 * @returns {Promise<void>}
 */
export async function runLiveChecks() {
    document.getElementById('liveChecks').classList.remove('d-none');

    let seasonLine = 'Could not read /legacy/season/current.';
    const seasonInfo = await loadCurrentSeason(); // the current season is another one now --> so are the defaults in step 1
    if (seasonInfo !== null) {
        seasonLine = 'Season ' + seasonInfo.seasonNumber + ' is live, updated ' + formatDateTime(seasonInfo.updatedAt)
            + ' UTC - browser extensions reload their cache based on that timestamp.';
    }

    document.getElementById('liveCheckSeason').textContent = seasonLine;
    const wasChecked = state.doneSteps.indexOf(3) !== -1; // ticked before the reload
    /** @type {NodeListOf<HTMLInputElement>} */ (document.querySelectorAll('#liveChecks input[type="checkbox"]')).forEach(checkbox => checkbox.checked = wasChecked);
}

export function updateLiveChecks() {
    let isEverythingChecked = true;
    /** @type {NodeListOf<HTMLInputElement>} */ (document.querySelectorAll('#liveChecks input[type="checkbox"]')).forEach(checkbox => {
        if (!checkbox.checked) isEverythingChecked = false;
    });

    if (isEverythingChecked && state.doneSteps.indexOf(3) === -1) completeStep(3);
}

/**
 * @returns {Promise<boolean>}
 */
export async function reloadCardCache() {
    const response = await request('./admin/v1/cards/reload', {method: 'POST'});
    if (!response.ok) {
        showError('commitError', await errorMessageOf(response));
        return false;
    }

    hideError('commitError');
    document.getElementById('reloadCacheButton').classList.add('d-none');

    return true;
}

// Steps 6 and 7 - the community bits

/**
 * @param {CommittedSeason | SeasonDraftReport} committed
 * @param {MtgSet[]} newSets
 */
export function renderAnnouncement(committed, newSets) {
    document.getElementById('step7Empty').classList.add('d-none');
    document.getElementById('step7Data').classList.remove('d-none');

    const lines = [
        '# WILLKOMMEN ZUR SEASON ' + committed.romanSeasonNumber,
        '',
        '@Casual Challengers',
        '',
        'Beginn: ' + formatGermanDate(committed.startDate),
        'Finales Event: ' + formatGermanDate(committed.finalsFriday),
        'Beginn Season ' + romanNumeral(committed.seasonNumber + 1) + ': ' + formatGermanDate(committed.nextSeasonStart),
        ''
    ];

    if (newSets.length === 0) {
        lines.push('Keine neuen Sets diese Season.');
    } else {
        const newCardCount = newSets.reduce((sum, mtgSet) => sum + mtgSet.newCardCount, 0);
        lines.push('**' + formatGermanNumber(newCardCount) + ' neue Karten insgesamt**');
        lines.push('');
        lines.push('Neu spielbare Sets:');
        newSets.forEach(mtgSet => {
            const childSets = mtgSet.childSets !== null && mtgSet.childSets !== undefined ? mtgSet.childSets : []; // a report of an older build only has the total
            const ownCardCount = childSets.reduce((count, childSet) => count - childSet.newCardCount, mtgSet.newCardCount);

            // Only the sub-sets brought new cards, their main set came out before --> they move up a level
            const childIndent = ownCardCount > 0 ? '  ' : '';
            if (ownCardCount > 0) lines.push('- ' + scryfallLinkOf(mtgSet) + ', ' + formatGermanNumber(ownCardCount) + ' neue Karten');
            childSets.forEach(childSet => lines.push(childIndent + '- ' + scryfallLinkOf(childSet) + ', ' + formatGermanNumber(childSet.newCardCount) + ' neue Karten'));
        });
    }

    lines.push('');
    lines.push('Ebenso haben sich die Bans und Unbans verändert, also schaut gerne in den Thread!');

    if (state.draftReport !== null && state.draftReport.metaSource === 'mtgtop8') {
        lines.push('');
        lines.push('Die Meta-Zahlen kommen diesmal von MTGTop8 (nur Main Deck), sie weichen also etwas ab.');
    }

    lines.push('');
    lines.push('<<Season Specials oder Regeländerungen kommen hier her.>>');

    /** @type {HTMLTextAreaElement} */ (document.getElementById('announcementText')).value = lines.join('\n');
    setOutcome('step7Outcome', 'post prepared');
}

/**
 * @param {MtgSet | ChildSet} mtgSet
 * @returns {string}
 */
function scryfallLinkOf(mtgSet) {
    return '[' + mtgSet.name + ' (' + mtgSet.code + ')](https://scryfall.com/sets/' + mtgSet.code.toLowerCase() + '?as=grid&order=set)';
}
