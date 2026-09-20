const TOKEN_STORAGE_KEY = 'casual-challenge-admin-token';
const DONE_STEPS_STORAGE_KEY = 'casual-challenge-done-steps';
const COMMIT_RESULT_STORAGE_KEY = 'casual-challenge-commit-result';
const OPEN_STEP_STORAGE_KEY = 'casual-challenge-open-step';
const POLL_INTERVAL = 3 * 1000;
// casual-challenge.season.length-in-weeks and price-window-days, only used for the "leave it empty, and you get this" lines
const SEASON_LENGTH_IN_DAYS = 10 * 7;
const PRICE_WINDOW_DAYS = 70;
const SQL_PARTS = ['00_add_season', '01_insert_cards', '02_insert_card_season_data'];
// Everything the server renders for step 2, 5 and 6
const REPORT_CONTAINERS = ['draftHeader', 'sanityChecks', 'commitBlockedReason', 'reportSections',
    'seasonHistoryTable', 'scryfallDecks'];
const MONTH_NAMES = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec'
];
const LAST_STEP = 7;
// What the header says once a step is ticked off, for the two that had a to-do in there
const DONE_OUTCOMES = {4: 'pushed and deployed', 7: 'posted'};

// SeasonPreparationStep: the five steps the status counts, stepNumber is 0 while it is still warming up
const PREPARATION_STEP_COUNT = 5;
// Seasons will not reach 90 before any of us is retired
const ROMAN_NUMERALS = [[50, 'L'], [40, 'XL'], [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']];

let adminToken = null;
let draftReport = null;
let currentSeason = null;
let preparationStatus = null;
let pollTimer = null;
let isStopping = false;
let hasBlockingSanityFailure = false;
let downloadedParts = [];
let doneSteps = [];
let removalPreview = null;

// Step 0 - the token

function decodeTokenPayload(token) {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    try {
        const binary = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
        const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
        return JSON.parse(new TextDecoder().decode(bytes));
    } catch (error) {
        return null;
    }
}

async function submitToken() {
    const token = document.getElementById('tokenInput').value.trim();
    if (token === '') {
        showError('tokenError', 'Paste the token first.');
        return;
    }

    const payload = decodeTokenPayload(token);
    if (payload === null) {
        showError('tokenError', 'Not a JWT. Copy what comes after "Token: ", nothing else.');
        return;
    }
    if (payload.admin !== true) {
        showError('tokenError', 'Normal API token. Seasons need one from generate-admin-jwt.');
        return;
    }
    if (payload.exp * 1000 < Date.now()) {
        showError('tokenError', 'Expired on ' + formatTimestamp(payload.exp * 1000) + '. Mint a new one.');
        return;
    }

    // Cheapest admin GET there is, and it answers IDLE before the first run --> it doubles as the door
    const response = await fetch('./admin/v1/season/preparation', {headers: {'Authorization': 'Bearer ' + token}});
    if (!response.ok) {
        showError('tokenError', 'Server turned it down: ' + await errorMessageOf(response));
        return;
    }

    adminToken = token;
    sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
    hideError('tokenError');
    document.getElementById('tokenGate').classList.add('d-none');
    document.getElementById('wizard').classList.remove('d-none');
    document.getElementById('tokenSubject').textContent = payload.sub;
    document.getElementById('tokenExpiry').textContent = 'Good until ' + formatTimestamp(payload.exp * 1000) + '.';
    setOutcome('step0Outcome', payload.sub);
    setStepDone(0, true);

    await loadCurrentSeason();
    renderPreparationStatus(await response.json());
    await loadDraft();
    restoreOpenStep();
}

function forgetToken(reason) {
    stopPolling();
    sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    sessionStorage.removeItem(DONE_STEPS_STORAGE_KEY);
    sessionStorage.removeItem(COMMIT_RESULT_STORAGE_KEY);
    sessionStorage.removeItem(OPEN_STEP_STORAGE_KEY);
    adminToken = null;
    draftReport = null;
    doneSteps = [];

    document.getElementById('wizard').classList.add('d-none');
    document.getElementById('tokenGate').classList.remove('d-none');
    document.getElementById('tokenInput').value = '';
    if (reason !== undefined) showError('tokenError', reason);
}

// Requests

async function request(path, options) {
    const requestOptions = options === undefined ? {} : options;
    requestOptions.headers = Object.assign({}, requestOptions.headers, {'Authorization': 'Bearer ' + adminToken});

    const response = await fetch(path, requestOptions);
    if (response.status === 401) {
        forgetToken('Token stopped working - expired, or the JWT secret was rotated.');
        throw new Error('The token was rejected.');
    }

    return response;
}

// The server sends one wrapper per container --> each [data-container] names where its html goes
function fillSections(html) {
    const template = document.createElement('template');
    template.innerHTML = html;
    template.content.querySelectorAll('[data-container]').forEach(wrapper => {
        document.getElementById(wrapper.dataset.container).replaceChildren(...wrapper.childNodes);
    });

    return template.content;
}

async function errorMessageOf(response) {
    const body = await response.text();
    if (body === '') return response.status + ' ' + response.statusText;

    // 400, 404 and 409 answer with Spring's error json, the security filters with plain text
    try {
        const parsed = JSON.parse(body);
        return parsed.message !== undefined && parsed.message !== null ? parsed.message : body;
    } catch (error) {
        return body;
    }
}

// Step 1 - preparing

async function startPreparation() {
    hideError('prepareError');

    const parameters = new URLSearchParams();
    appendDateParameter(parameters, 'startDate');
    appendDateParameter(parameters, 'endDate');
    appendDateParameter(parameters, 'priceWindowStart');
    appendDateParameter(parameters, 'priceWindowEnd');

    const metaSource = metaSourceValue();
    if (metaSource !== 'mtggoldfish') parameters.set('metaSource', metaSource);

    const requestOptions = {method: 'POST'};
    if (metaSource === 'files') {
        const bansFile = document.getElementById('bansFile').files[0];
        const extendedBansFile = document.getElementById('extendedBansFile').files[0];
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

function appendDateParameter(parameters, elementId) {
    const value = document.getElementById(elementId).value;
    if (value !== '') parameters.set(elementId, value);
}

async function cancelPreparation() {
    isStopping = true;
    const cancelButton = document.getElementById('cancelButton');
    cancelButton.disabled = true;
    showSpinner(cancelButton, 'Stopping...');

    const response = await request('./admin/v1/season/preparation', {method: 'DELETE'});
    if (!response.ok) {
        isStopping = false;
        cancelButton.disabled = false;
        cancelButton.textContent = 'Give up on it';
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    await pollPreparation(); // waiting out the poll interval first looks like nothing happened
}

function startPolling() {
    if (pollTimer !== null) return;

    pollTimer = setInterval(pollPreparation, POLL_INTERVAL);
}

function stopPolling() {
    if (pollTimer === null) return;

    clearInterval(pollTimer);
    pollTimer = null;
}

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

    await loadDraft();
    openStep('step2Body');
}

function metaSourceValue() {
    return document.querySelector('input[name="metaSource"]:checked').value;
}

function renderPreparationStatus(status) {
    preparationStatus = status;
    const isRunning = status.state === 'RUNNING';

    document.getElementById('preparationProgress').classList.toggle('d-none', status.state === 'IDLE');
    document.getElementById('cancelButton').classList.toggle('d-none', !isRunning);
    document.getElementById('prepareButton').disabled = isRunning;
    document.getElementById('preparationClock').textContent = clockText(status);

    document.querySelectorAll('#preparationSteps .preparation-step').forEach(element => {
        const step = Number(element.dataset.step);
        element.classList.toggle('is-done', status.state === 'DONE' || step < status.stepNumber);
        element.classList.toggle('is-current', isRunning && step === status.stepNumber);
    });

    if (!isRunning && isStopping) {
        isStopping = false;
        document.getElementById('cancelButton').disabled = false;
        document.getElementById('cancelButton').textContent = 'Give up on it';
    }

    if (status.state === 'FAILED') {
        showError('prepareError', status.errorMessage !== null ? status.errorMessage : 'It gave up without saying why.');
    }

    if (isRunning) startPolling();
    setOutcome('step1Outcome', preparationOutcome(status));
}

// Nothing ran, or what ran has nothing to show for it anymore --> step 1 from the top
function resetPreparation() {
    if (preparationStatus !== null && (preparationStatus.state === 'RUNNING' || preparationStatus.state === 'FAILED')) return;

    document.getElementById('preparationProgress').classList.add('d-none');
    document.getElementById('preparationClock').textContent = '';
    document.querySelectorAll('#preparationSteps .preparation-step').forEach(element => {
        element.classList.remove('is-done');
        element.classList.remove('is-current');
    });

    setOutcome('step1Outcome', 'nothing running');
    setStepDone(1, false);
}

function preparationOutcome(status) {
    switch (status.state) {
        case 'IDLE':
            return 'nothing running';
        case 'RUNNING':
            if (isStopping) return 'stopping, ' + status.step;

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

function clockText(status) {
    if (status.startedAt === null || status.startedAt === undefined) return '';

    const startedAt = Date.parse(status.startedAt + 'Z'); // the API writes UTC without saying so
    const finishedAt = status.finishedAt !== null && status.finishedAt !== undefined
        ? Date.parse(status.finishedAt + 'Z')
        : Date.now();
    const seconds = Math.max(0, Math.round((finishedAt - startedAt) / 1000));
    const elapsed = seconds < 60 ? seconds + ' s' : Math.floor(seconds / 60) + ' min ' + (seconds % 60) + ' s';

    if (status.state !== 'RUNNING') return 'Took ' + elapsed + '.';
    if (isStopping) return 'Running for ' + elapsed + '. Stopping after the current step.';

    return 'Running for ' + elapsed + '.';
}

async function loadCurrentSeason() {
    const response = await fetch('./legacy/season/current');
    currentSeason = response.ok ? await response.json() : null;
    renderCurrentSeason();
    renderPreparationDefaults();

    return currentSeason;
}

// Where we stand before anything is touched: the season that is live right now and how it is doing on time
function renderCurrentSeason() {
    document.getElementById('currentSeasonEmpty').classList.toggle('d-none', currentSeason !== null);
    document.getElementById('currentSeasonData').classList.toggle('d-none', currentSeason === null);
    if (currentSeason === null) return;

    const today = todayInUtc();
    const daysSinceStart = daysBetween(currentSeason.startDate, today);
    const daysUntilEnd = daysBetween(today, currentSeason.endDate);
    const isOverdue = daysUntilEnd < 0;

    document.getElementById('currentSeasonNumber').textContent = currentSeason.seasonNumber
        + ' (' + romanNumeral(currentSeason.seasonNumber) + ')';
    document.getElementById('currentSeasonStarted').textContent = formatDate(currentSeason.startDate) + ', ' + agoText(daysSinceStart);
    document.getElementById('currentSeasonScheduled').textContent = (isOverdue ? 'ended ' : 'ends ')
        + formatDate(currentSeason.endDate) + ', ' + endsText(daysUntilEnd);
    document.getElementById('currentSeasonScheduled').classList.toggle('text-warning', isOverdue);
    document.getElementById('currentSeasonRunning').textContent = lengthText(daysBetween(currentSeason.startDate, currentSeason.endDate) + 1)
        + ' planned, ' + lengthText(daysSinceStart + 1) + ' so far';
    document.getElementById('currentSeasonUpdated').textContent = 'updated ' + formatDateTime(currentSeason.updatedAt) + ' UTC';
}

// The dates SeasonPreparationService.withDefaults would pick. A date input ignores its placeholder --> below the field
function renderPreparationDefaults() {
    const chosenStartDate = document.getElementById('startDate').value;
    const startDate = chosenStartDate !== '' ? chosenStartDate : todayInUtc();

    document.getElementById('startDateDefault').textContent = 'default: ' + formatDate(todayInUtc()) + ', today in UTC';
    document.getElementById('endDateDefault').textContent = endDateDefaultText(startDate);
    document.getElementById('priceWindowStartDefault').textContent = 'default:  '
        + formatDate(isoDatePlusDays(startDate, -PRICE_WINDOW_DAYS)) + ', ' + PRICE_WINDOW_DAYS + ' days before the start';
    document.getElementById('priceWindowEndDefault').textContent = 'default:  ' + formatDate(startDate)
        + ', the start date, end exclusive';
}

function endDateDefaultText(startDate) {
    if (currentSeason === null) return 'default: 10 weeks after the current season ends';

    const afterCurrentSeason = isoDatePlusDays(currentSeason.endDate, SEASON_LENGTH_IN_DAYS);
    if (afterCurrentSeason > startDate) {
        return 'default: ' + formatDate(afterCurrentSeason) + ', 10 weeks after season ' + currentSeason.seasonNumber + ' ends';
    }

    // Season ## ended ages ago --> the API counts the ten weeks from the new start instead
    return 'default: ' + formatDate(isoDatePlusDays(startDate, SEASON_LENGTH_IN_DAYS - 1)) + ', 10 weeks from the start, season '
        + currentSeason.seasonNumber + ' is long over';
}

// Step 2 - the draft

async function loadDraft() {
    const response = await request('./admin/v1/season/draft');
    if (response.status === 404) {
        draftReport = null;
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

    draftReport = withEmptyLists(report);
    downloadedParts = [];
    restoreDoneSteps(draftReport);

    if (sectionsResponse.ok) {
        hideError('draftError');
        const sanityChecks = fillSections(sectionsBody).querySelector('[data-container="sanityChecks"]');
        hasBlockingSanityFailure = sanityChecks === null || sanityChecks.dataset.commitBlocked !== 'false'; // no verdict --> no commit without the checkbox
    } else {
        showError('draftError', sectionsBody);
        renderMissingReport();
        hasBlockingSanityFailure = true;
    }

    renderDraft(draftReport);
    if (draftReport.committedAt !== null) {
        restoreCommitResult(draftReport);
        await runLiveChecks();
    }
}

// The draft is there, its report is not --> the one of the draft before it must not stay on screen next to it
function renderMissingReport() {
    REPORT_CONTAINERS.forEach(container => document.getElementById(container).replaceChildren());
    document.getElementById('commitBlockedReason').textContent = 'The report didn\'t load, so nobody checked the draft.';
}

// Reports written by older builds leave some of the lists out entirely --> don't let one null take the page down
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
    hasBlockingSanityFailure = false;
    updateCommitButton();
}

function renderDraft(report) {
    document.getElementById('commitAnyway').checked = false;
    document.getElementById('discardButton').classList.toggle('d-none', report.committedAt !== null); // a committed draft stays, it is the record of that season start
    ['step2', 'step4', 'step5', 'step6'].forEach(step => {
        document.getElementById(step + 'Empty').classList.add('d-none');
        document.getElementById(step + 'Data').classList.remove('d-none');
    });

    document.getElementById('commitBlocked').classList.toggle('d-none', !hasBlockingSanityFailure);
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

async function discardDraft() {
    if (!window.confirm('Throw the draft for season ' + draftReport.seasonNumber + ' away?')) return;

    const response = await request('./admin/v1/season/draft', {method: 'DELETE'});
    if (!response.ok) {
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    resetPreparation();
    await loadDraft(); // a DELETE hands back the last committed draft, if there is one
    openStep('step1Body');
}

// Step 3 - committing

function updateCommitButton() {
    const hasDraft = draftReport !== null;
    const isCommitted = hasDraft && draftReport.committedAt !== null;
    const isOverridden = document.getElementById('commitAnyway').checked;

    document.getElementById('commitButton').disabled = !hasDraft || isCommitted || (hasBlockingSanityFailure && !isOverridden);
    document.getElementById('commitButton').textContent = isCommitted ? 'Already committed' : 'Commit the season';
}

async function commitSeason() {
    const githubToken = document.getElementById('githubTokenInput').value.trim();
    const question = githubToken === ''
        ? 'Commit season ' + draftReport.seasonNumber + '? /v1/cards waits a second or two while it goes in.'
        : 'Commit season ' + draftReport.seasonNumber + ' and open a pull request with the migrations?';
    if (!window.confirm(question)) return;

    hideError('commitError');
    document.getElementById('commitButton').disabled = true;
    document.getElementById('reloadCacheButton').classList.add('d-none');

    const response = await request('./admin/v1/season/commit', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({githubToken: githubToken === '' ? null : githubToken})
    });
    if (!response.ok) {
        const message = await errorMessageOf(response);
        showError('commitError', response.status === 409
            ? message + ' Either it is in already, or the current season changed since the preparation --> prepare again.'
            : message);
        // The one failure that leaves a committed season behind names its own fix
        document.getElementById('reloadCacheButton').classList.toggle('d-none', message.indexOf('cards/reload') === -1);
        updateCommitButton();
        return;
    }

    document.getElementById('githubTokenInput').value = ''; // it did its job, no reason to keep it around
    const committed = await response.json();
    sessionStorage.setItem(COMMIT_RESULT_STORAGE_KEY, JSON.stringify({
        seasonNumber: committed.seasonNumber,
        committed: committed
    }));
    await loadDraft(); // now it has committedAt --> the sql files get their final names, and the result comes back out of the storage
}

// The counts and the pull request number are in that one answer and nowhere else - no GET replays them
function restoreCommitResult(report) {
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

function renderCommitted(committed) {
    const isOpened = committed.pullRequest !== null && committed.pullRequest !== undefined;

    document.getElementById('committedAlertSeason').textContent = committed.seasonNumber;
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

async function runLiveChecks() {
    document.getElementById('liveChecks').classList.remove('d-none');

    let seasonLine = 'Could not read /legacy/season/current.';
    const seasonInfo = await loadCurrentSeason(); // the current season is another one now --> so are the defaults in step 1
    if (seasonInfo !== null) {
        seasonLine = 'Season ' + seasonInfo.seasonNumber + ' is live, updated ' + formatDateTime(seasonInfo.updatedAt)
            + ' UTC - browser extensions reload their cache based on that timestamp.';
    }

    document.getElementById('liveCheckSeason').textContent = seasonLine;
    const wasChecked = doneSteps.indexOf(3) !== -1; // ticked before the reload
    document.querySelectorAll('#liveChecks input[type="checkbox"]').forEach(checkbox => checkbox.checked = wasChecked);
}

function updateLiveChecks() {
    let isEverythingChecked = true;
    document.querySelectorAll('#liveChecks input[type="checkbox"]').forEach(checkbox => {
        if (!checkbox.checked) isEverythingChecked = false;
    });

    if (isEverythingChecked && doneSteps.indexOf(3) === -1) completeStep(3);
}

async function reloadCardCache() {
    const response = await request('./admin/v1/cards/reload', {method: 'POST'});
    if (!response.ok) {
        showError('commitError', await errorMessageOf(response));
        return;
    }

    hideError('commitError');
    document.getElementById('reloadCacheButton').classList.add('d-none');
    await loadDraft(); // the draft is committed in this one, so the verification comes with it
}

// Step 4 - the migrations

function renderDownloads(report) {
    document.getElementById('downloadWarning').classList.toggle('d-none', report.committedAt !== null);
    setOutcome('step4Outcome', '');
    renderPullRequest(report.pullRequestUrl);
    markGitChecklist();
}

function renderPullRequest(pullRequestUrl) {
    const isOpened = hasPullRequest();
    document.getElementById('pullRequestResult').classList.toggle('d-none', !isOpened);
    if (!isOpened) return;

    const number = pullRequestUrl.substring(pullRequestUrl.lastIndexOf('/') + 1);
    document.getElementById('pullRequestLink').href = pullRequestUrl;
    document.getElementById('pullRequestNumber').textContent = number;
    setOutcome('step4Outcome', 'pull request #' + number + ' opened');
    setStepDone(4, true);
}

function hasPullRequest() {
    return draftReport !== null && draftReport.pullRequestUrl !== null && draftReport.pullRequestUrl !== undefined;
}

// A pull request does 1 to 4 on its own, without one the downloads are the only entry the page can tick
function markGitChecklist() {
    document.querySelectorAll('#gitChecklist .checklist-step').forEach(element => {
        const step = Number(element.dataset.checklistStep);
        const isDone = hasPullRequest() ? step <= 4 : step === 1 && downloadedParts.length === SQL_PARTS.length;
        element.classList.toggle('is-done', isDone);
    });
}

async function downloadAllSql() {
    for (let index = 0; index < SQL_PARTS.length; index++) {
        const wasDownloaded = await downloadSql(SQL_PARTS[index]);
        if (!wasDownloaded) return; // the other two would fail the same way
    }
}

async function downloadSql(part) {
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

    if (downloadedParts.indexOf(part) === -1) downloadedParts.push(part);
    if (!hasPullRequest()) setOutcome('step4Outcome', downloadedParts.length + ' of ' + SQL_PARTS.length + ' downloaded');
    markGitChecklist();

    return true;
}

// Same rule as SeasonDraftService.sqlFileName, for when the header doesn't survive the trip
function fileNameOf(response, part) {
    const disposition = response.headers.get('Content-Disposition');
    if (disposition !== null) {
        const match = disposition.match(/filename="([^"]+)"/);
        if (match !== null) return match[1];
    }

    const writtenAt = draftReport.committedAt !== null ? draftReport.committedAt : draftReport.preparedAt;
    const prefix = writtenAt.substring(0, 16).replace(/[-:]/g, '').replace('T', '_');
    switch (part) {
        case '00_add_season':
            return prefix + '_' + part + '_' + draftReport.seasonNumber + '.sql';
        case '02_insert_card_season_data':
            return prefix + '_' + part + '_for_season_' + draftReport.seasonNumber + '.sql';
        default:
            return prefix + '_' + part + '.sql';
    }
}

// Step 5 - the Season History row

// Google Docs drops the html into the selected cells, everything else gets the tab separated version
async function copySeasonHistoryRow(elementId) {
    const row = document.getElementById(elementId);
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

// Steps 6 and 7 - the community bits

function cardCountOf(deckList) {
    if (deckList === null || deckList === undefined || deckList.trim() === '') return '0 cards';

    return deckList.trim().split('\n').length + ' cards';
}

function renderAnnouncement(committed, newSets) {
    document.getElementById('step7Empty').classList.add('d-none');
    document.getElementById('step7Data').classList.remove('d-none');

    const lines = [
        'Season ' + committed.seasonNumber + ' (' + committed.romanSeasonNumber + ')',
        'Starts: ' + formatDate(committed.startDate),
        'Finals Friday: ' + formatDate(committed.finalsFriday),
        'Ends: ' + formatDate(committed.endDate),
        'Next season starts: ' + formatDate(committed.nextSeasonStart),
        ''
    ];

    if (newSets.length === 0) {
        lines.push('No new sets this season.');
    } else {
        lines.push('Newly playable sets:');
        newSets.forEach(mtgSet => {
            lines.push('- ' + mtgSet.name + ' (' + mtgSet.code + '), ' + mtgSet.newCardCount + ' new cards');
            if (mtgSet.commanderDecks !== null && mtgSet.commanderDecks.length > 0) {
                lines.push('  Commander decks: ' + mtgSet.commanderDecks.join(', '));
            }
        });
    }

    if (draftReport !== null && draftReport.metaSource === 'mtgtop8') {
        lines.push('');
        lines.push('Staples from MTGTop8 this time - main deck only, so the numbers differ a bit.');
    }

    document.getElementById('announcementText').value = lines.join('\n');
    setOutcome('step7Outcome', 'facts prepared');
}

// Removing a season

async function loadRemovalPreview() {
    hideError('removalError');
    document.getElementById('removalPreview').classList.add('d-none');
    document.getElementById('removalConfirm').classList.add('d-none');
    document.getElementById('removalResult').classList.add('d-none');
    removalPreview = null;

    const seasonNumber = document.getElementById('removalSeasonInput').value.trim();
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

    removalPreview = preview;
    fillSections(sectionsBody);
    renderRemovalPreview(preview);
}

function renderRemovalPreview(preview) {
    document.getElementById('removalPreview').classList.remove('d-none');
    document.getElementById('removalTokenBlock').classList.toggle('d-none', preview.pullRequestUrl === null);
    document.getElementById('removalConfirmInput').value = '';
    document.getElementById('removalConfirm').classList.toggle('d-none', !preview.removable);
    updateRemovalButton();
}

function updateRemovalButton() {
    const isConfirmed = removalPreview !== null
        && document.getElementById('removalConfirmInput').value.trim() === String(removalPreview.seasonNumber);

    document.getElementById('removalButton').disabled = !isConfirmed;
}

async function removeSeason() {
    if (!window.confirm('Remove season ' + removalPreview.seasonNumber + '? Season ' + removalPreview.previousSeasonNumber + ' is the current one afterwards.')) return;

    hideError('removalError');
    const removalButton = document.getElementById('removalButton');
    removalButton.disabled = true;
    showSpinner(removalButton, 'Removing...');

    const githubToken = document.getElementById('removalGithubToken').value.trim();
    const response = await request('./admin/v1/season/' + removalPreview.seasonNumber + '/removal', {
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

function renderRemoved(removed) {
    document.getElementById('removedSeason').textContent = removed.seasonNumber;
    document.getElementById('removedPreviousSeason').textContent = removed.previousSeasonNumber;
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
    document.getElementById('removalGithubToken').value = '';
    document.getElementById('removalResult').classList.remove('d-none');
    removalPreview = null;
}

// The checkmarks

function setStepDone(step, isDone) {
    document.getElementById('step' + step + 'Body').closest('.accordion-item').classList.toggle('is-done', isDone);
}

function completeStep(step) {
    if (doneSteps.indexOf(step) === -1) doneSteps.push(step);
    sessionStorage.setItem(DONE_STEPS_STORAGE_KEY, JSON.stringify({
        seasonNumber: draftReport.seasonNumber,
        steps: doneSteps
    }));
    renderDoneSteps();

    if (step === LAST_STEP) {
        closeStep('step' + step + 'Body');
        return;
    }

    openStep('step' + (step + 1) + 'Body'); // opening the next one closes this one, they share the accordion
}

function renderDoneSteps() {
    doneSteps.forEach(step => {
        setStepDone(step, true);
        if (DONE_OUTCOMES[step] !== undefined) setOutcome('step' + step + 'Outcome', DONE_OUTCOMES[step]);
    });
}

// Ticked off by hand, so they survive a reload - as long as it is still the same season
function restoreDoneSteps(report) {
    doneSteps = [];

    const stored = sessionStorage.getItem(DONE_STEPS_STORAGE_KEY);
    if (stored === null) return;

    try {
        const parsed = JSON.parse(stored);
        if (parsed.seasonNumber === report.seasonNumber) doneSteps = parsed.steps;
    } catch (error) {
        sessionStorage.removeItem(DONE_STEPS_STORAGE_KEY);
    }
}

function forgetDoneSteps() {
    doneSteps = [];
    sessionStorage.removeItem(DONE_STEPS_STORAGE_KEY);

    for (let step = 1; step <= LAST_STEP; step++) setStepDone(step, false); // 0 is the token, that one is still there
}

// Odds and ends

function showError(elementId, message) {
    const element = document.getElementById(elementId);
    element.textContent = message;
    element.classList.remove('d-none');
}

function hideError(elementId) {
    document.getElementById(elementId).classList.add('d-none');
}

function setOutcome(elementId, text) {
    document.getElementById(elementId).textContent = text;
}

function showSpinner(button, label) {
    const spinner = document.createElement('span');
    spinner.className = 'spinner-border spinner-border-sm';
    button.replaceChildren(spinner, ' ' + label);
}

function openStep(bodyId) {
    bootstrap.Collapse.getOrCreateInstance(document.getElementById(bodyId)).show();
}

function closeStep(bodyId) {
    bootstrap.Collapse.getOrCreateInstance(document.getElementById(bodyId)).hide();
}

// Back where you were after a reload - waiting for the deploy with step 4 open shouldn't cost you the step
function restoreOpenStep() {
    const bodyId = sessionStorage.getItem(OPEN_STEP_STORAGE_KEY);
    if (bodyId === null) return;
    if (document.getElementById(bodyId) === null) return; // stored by a build that numbered the steps differently

    openStep(bodyId);
}

function flashCopied(button) {
    const label = button.textContent;
    button.textContent = 'Copied';
    setTimeout(() => {
        button.textContent = label;
    }, 1500);
}

// Drafts from before the admin name was tracked have neither prepared_by nor committed_by
function byLine(name) {
    return name === null || name === undefined ? '' : ' by ' + name;
}

function formatNumber(value) {
    if (value === null || value === undefined) return '';

    return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function formatDate(isoDate) {
    if (isoDate === null || isoDate === undefined) return '';

    const parts = isoDate.split('-');

    return Number(parts[2]) + ' ' + MONTH_NAMES[Number(parts[1]) - 1] + ' ' + parts[0];
}

function todayInUtc() {
    return new Date().toISOString().substring(0, 10);
}

function isoDatePlusDays(isoDate, days) {
    return new Date(Date.parse(isoDate + 'T00:00:00Z') + days * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);
}

function daysBetween(fromIsoDate, toIsoDate) {
    return Math.round((Date.parse(toIsoDate + 'T00:00:00Z') - Date.parse(fromIsoDate + 'T00:00:00Z')) / (24 * 60 * 60 * 1000));
}

function dayText(days) {
    return days + (days === 1 ? ' day' : ' days');
}

function agoText(days) {
    if (days === 0) return 'today';
    if (days === 1) return 'yesterday';

    return days + ' days ago';
}

function endsText(days) {
    if (days < 0) return agoText(-days);
    if (days === 0) return 'today';

    return 'in ' + dayText(days);
}

function lengthText(days) {
    if (days < 14) return dayText(days);

    const weeks = Math.floor(days / 7);
    const remainingDays = days % 7;

    return weeks + ' weeks' + (remainingDays === 0 ? '' : ' and ' + dayText(remainingDays));
}

function romanNumeral(seasonNumber) {
    let rest = seasonNumber;
    let roman = '';
    ROMAN_NUMERALS.forEach(numeral => {
        while (rest >= numeral[0]) {
            roman += numeral[1];
            rest -= numeral[0];
        }
    });

    return roman;
}

function formatDateTime(isoDateTime) {
    if (isoDateTime === null || isoDateTime === undefined) return '';

    const parts = isoDateTime.split('T');

    return formatDate(parts[0]) + ', ' + parts[1].substring(0, 5);
}

function formatTimestamp(milliseconds) {
    const date = new Date(milliseconds);

    return date.getDate() + ' ' + MONTH_NAMES[date.getMonth()] + ' ' + date.getFullYear();
}

function wireUpControls() {
    document.getElementById('tokenSubmit').addEventListener('click', submitToken);
    document.getElementById('tokenInput').addEventListener('keydown', event => {
        if (event.key === 'Enter') submitToken();
    });
    document.getElementById('tokenForget').addEventListener('click', () => forgetToken());

    document.querySelectorAll('input[name="metaSource"]').forEach(radio => radio.addEventListener('change', () => {
        document.getElementById('mtgTop8warning').classList.toggle('d-none', metaSourceValue() !== 'mtgtop8');
        document.getElementById('banFileInputs').classList.toggle('d-none', metaSourceValue() !== 'files');
    }));
    document.getElementById('startDate').addEventListener('change', renderPreparationDefaults);
    document.getElementById('prepareButton').addEventListener('click', startPreparation);
    document.getElementById('cancelButton').addEventListener('click', cancelPreparation);
    document.getElementById('discardButton').addEventListener('click', discardDraft);

    document.getElementById('stepAccordion').addEventListener('shown.bs.collapse', event => {
        sessionStorage.setItem(OPEN_STEP_STORAGE_KEY, event.target.id);
    });
    // Opening the next step closes the current one, so only an accordion with nothing open at all forgets it
    document.getElementById('stepAccordion').addEventListener('hidden.bs.collapse', () => {
        if (document.querySelector('#stepAccordion .accordion-collapse.show') === null) {
            sessionStorage.removeItem(OPEN_STEP_STORAGE_KEY);
        }
    });

    document.getElementById('removalPreviewButton').addEventListener('click', loadRemovalPreview);
    document.getElementById('removalConfirmInput').addEventListener('input', updateRemovalButton);
    document.getElementById('removalButton').addEventListener('click', removeSeason);

    document.getElementById('commitAnyway').addEventListener('change', updateCommitButton);
    document.getElementById('commitButton').addEventListener('click', commitSeason);
    document.getElementById('reloadCacheButton').addEventListener('click', reloadCardCache);
    document.getElementById('liveChecks').addEventListener('change', updateLiveChecks);
    document.getElementById('downloadAllButton').addEventListener('click', downloadAllSql);

    document.addEventListener('click', async event => {
        const downloadButton = event.target.closest('[data-sql-part]');
        if (downloadButton !== null) {
            await downloadSql(downloadButton.dataset.sqlPart);
            return;
        }

        const doneButton = event.target.closest('[data-done-step]');
        if (doneButton !== null) {
            completeStep(Number(doneButton.dataset.doneStep));
            return;
        }

        const copyRowButton = event.target.closest('[data-copy-html]');
        if (copyRowButton !== null) {
            await copySeasonHistoryRow(copyRowButton.dataset.copyHtml);
            flashCopied(copyRowButton);
            return;
        }

        const copyButton = event.target.closest('[data-copy-target]');
        if (copyButton === null) return;

        const source = document.getElementById(copyButton.dataset.copyTarget);
        await navigator.clipboard.writeText(source.value !== undefined ? source.value : source.textContent);
        flashCopied(copyButton);
    });
}

wireUpControls();

const storedToken = sessionStorage.getItem(TOKEN_STORAGE_KEY);
if (storedToken !== null) {
    document.getElementById('tokenInput').value = storedToken;
    // noinspection JSIgnoredPromiseFromCall
    submitToken();
}
