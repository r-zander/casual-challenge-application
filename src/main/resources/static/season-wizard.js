const TOKEN_STORAGE_KEY = 'casual-challenge-admin-token';
const DONE_STEPS_STORAGE_KEY = 'casual-challenge-done-steps';
const OPEN_STEP_STORAGE_KEY = 'casual-challenge-open-step';
const POLL_INTERVAL = 3 * 1000;
const EXPECTED_TOP_50_ROWS = 50;
const EXPECTED_TOP_150_ROWS = 150;
const PLAUSIBLE_CARD_COUNT_MINIMUM = 25000;
const PLAUSIBLE_CARD_COUNT_MAXIMUM = 45000;
const PLAUSIBLE_EXCHANGE_RATE_MINIMUM = 1.0;
const PLAUSIBLE_EXCHANGE_RATE_MAXIMUM = 2.5;
const ALLOWED_MISSING_PRICE_DAYS = 3; // MTGJSON drops the odd day, 68 of 70 is a normal window
// casual-challenge.season.length-in-weeks and price-window-days, only used for the "leave it empty, and you get this" lines
const SEASON_LENGTH_IN_DAYS = 10 * 7;
const PRICE_WINDOW_DAYS = 70;
const SQL_PARTS = ['00_add_season', '01_insert_cards', '02_insert_card_season_data'];
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
// The columns of the Season History table in CC - Organisation, in that order
const SEASON_HISTORY_COLUMNS = ['Season', 'Start', 'Ende', 'Neu spielbare Sets', 'Set Code', 'Updates'];
const LAST_STEP = 7;
// What the header says once a step is ticked off, for the two that had a to-do in there
const DONE_OUTCOMES = {4: 'pushed and deployed', 7: 'posted'};

// The five steps of SeasonPreparationService, matched by prefix - step 3 carries the meta source in its text
const PREPARATION_STEPS = [
    'Reading AllPrintings.json',
    'Reading AllPrices.json',
    'Reading meta shares from',
    'Calculating budget points',
    'Storing the season draft'
];

let adminToken = null;
let draftReport = null;
let currentSeason = null;
let pollTimer = null;
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
    const response = await request('./admin/v1/season/preparation', {method: 'DELETE'});
    if (!response.ok) showError('prepareError', await errorMessageOf(response));
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
    const isRunning = status.state === 'RUNNING';
    const stepIndex = preparationStepIndex(status.step);

    document.getElementById('preparationProgress').classList.toggle('d-none', status.state === 'IDLE');
    document.getElementById('cancelButton').classList.toggle('d-none', !isRunning);
    document.getElementById('prepareButton').disabled = isRunning;
    document.getElementById('preparationClock').textContent = clockText(status);

    document.querySelectorAll('#preparationSteps .preparation-step').forEach(element => {
        const step = Number(element.dataset.step);
        element.classList.toggle('is-done', status.state === 'DONE' || step < stepIndex);
        element.classList.toggle('is-current', isRunning && step === stepIndex);
    });

    if (status.state === 'FAILED') {
        showError('prepareError', status.errorMessage !== null ? status.errorMessage : 'It gave up without saying why.');
    }

    if (isRunning) startPolling();
    setOutcome('step1Outcome', preparationOutcome(status));
}

function preparationStepIndex(step) {
    if (step === null || step === undefined) return 0;

    for (let index = 0; index < PREPARATION_STEPS.length; index++) {
        if (step.startsWith(PREPARATION_STEPS[index])) return index + 1;
    }

    return 0; // "Untap, Upkeep, Draw!" and the closing line are neither of the five
}

function preparationOutcome(status) {
    const stepIndex = preparationStepIndex(status.step);

    switch (status.state) {
        case 'IDLE':
            return 'nothing running';
        case 'RUNNING':
            // 0 is the warm-up line and the closing one, neither of them is one of the five steps
            return stepIndex === 0 ? status.step : 'step ' + stepIndex + ' of ' + PREPARATION_STEPS.length + ', ' + status.step;
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

    return status.state === 'RUNNING' ? 'Running for ' + elapsed + '.' : 'Took ' + elapsed + '.';
}

async function loadCurrentSeason() {
    const response = await fetch('./legacy/season/current');
    currentSeason = response.ok ? await response.json() : null;
    renderPreparationDefaults();

    return currentSeason;
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

    draftReport = withEmptyLists(await response.json());
    downloadedParts = [];
    restoreDoneSteps(draftReport);
    renderDraft(draftReport);
}

// Reports written by older builds leave some of the lists out entirely --> don't let one null take the page down
function withEmptyLists(report) {
    ['duplicateMetaShareNames', 'newBans', 'unbans', 'newExtended', 'noLongerExtended',
        'budgetPointChanges', 'topIncreases', 'topDecreases', 'zeroBudgetPointCards',
        'missingCards', 'skippedCards', 'oracleIdChanges', 'renamedCards', 'normalizedNameFixes',
        'setsReleased'].forEach(field => {
        if (report[field] === null || report[field] === undefined) report[field] = [];
    });
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
    document.getElementById('commitResult').innerHTML = '';
    document.getElementById('liveChecks').classList.add('d-none');

    ['step2Outcome', 'step3Outcome', 'step4Outcome', 'step5Outcome', 'step6Outcome', 'step7Outcome']
        .forEach(outcome => setOutcome(outcome, ''));
    forgetDoneSteps();
    hasBlockingSanityFailure = false;
    updateCommitButton();
}

function renderDraft(report) {
    document.getElementById('commitAnyway').checked = false;
    ['step2', 'step4', 'step5', 'step6'].forEach(step => {
        document.getElementById(step + 'Empty').classList.add('d-none');
        document.getElementById(step + 'Data').classList.remove('d-none');
    });

    renderDraftHeader(report);
    renderSanityChecks(report);
    renderReportSections(report);
    renderSeasonHistory(report);
    renderDownloads(report);
    renderScryfallDecks(report.scryfallDecks);
    updateCommitButton();

    setStepDone(1, true);
    setStepDone(3, report.committedAt !== null);

    setOutcome('step2Outcome', 'season ' + report.seasonNumber + ', ' + formatNumber(report.counts.cards) + ' cards, '
        + report.counts.newBans + ' new bans, ' + report.counts.unbans + ' unbans');

    if (report.committedAt === null) {
        setOutcome('step3Outcome', 'not committed');
    } else {
        setOutcome('step3Outcome', 'committed ' + formatDateTime(report.committedAt) + byLine(report.committedBy));
        renderAnnouncement(report, report.setsReleased); // committed before the page was opened --> the facts come out of the report
    }

    renderDoneSteps(); // after the outcomes above, two of them get overwritten
}

function renderDraftHeader(report) {
    const rows = [
        ['Season', report.seasonNumber + ' (' + report.romanSeasonNumber + '), following season ' + report.previousSeasonNumber],
        ['Runs', formatDate(report.startDate) + ' until ' + formatDate(report.endDate)],
        ['Finals Friday', formatDate(report.finalsFriday)],
        ['Next season starts', formatDate(report.nextSeasonStart)],
        ['Price window', formatDate(report.priceWindowStart) + ' until ' + formatDate(report.priceWindowEnd) + ', end exclusive'],
        ['MTGJSON', report.mtgJsonVersion + ' of ' + formatDate(report.mtgJsonDate)],
        ['Staples from', report.metaSource],
        ['Prepared', formatDateTime(report.preparedAt) + ' UTC' + byLine(report.preparedBy)]
    ];
    if (report.committedAt !== null) {
        rows.push(['Committed', formatDateTime(report.committedAt) + ' UTC' + byLine(report.committedBy)]);
    }

    document.getElementById('draftHeader').innerHTML = '<table class="table table-sm data-table mb-0">'
        + rows.map(row => '<tr><th class="fw-normal text-body-secondary">' + row[0] + '</th><td>' + escapeHtml(row[1]) + '</td></tr>').join('')
        + '</table>';
}

function renderSanityChecks(report) {
    const counts = report.counts;
    const checks = [];

    checks.push(check('Cards', formatNumber(counts.cards) + ', ' + formatNumber(counts.newCards) + ' of them new',
        counts.cards >= PLAUSIBLE_CARD_COUNT_MINIMUM && counts.cards <= PLAUSIBLE_CARD_COUNT_MAXIMUM, false, 'around 33k'));
    checks.push(check('Priced days', counts.pricedDays + ' of ' + priceWindowDays(report),
        counts.pricedDays >= priceWindowDays(report) - ALLOWED_MISSING_PRICE_DAYS, false, 'the whole window, give or take a day'));
    checks.push(check('Top 50 rows', rowCountSummary(counts.top50Rows, EXPECTED_TOP_50_ROWS),
        isEveryFormatAt(counts.top50Rows, EXPECTED_TOP_50_ROWS), true, '50 per format'));
    checks.push(check('Top 150 rows', rowCountSummary(counts.top150Rows, EXPECTED_TOP_150_ROWS),
        isEveryFormatAt(counts.top150Rows, EXPECTED_TOP_150_ROWS), true, '150 per format'));
    checks.push(check('Cards counted twice', report.duplicateMetaShareNames.length === 0 ? 'none' : report.duplicateMetaShareNames.join(', '),
        report.duplicateMetaShareNames.length === 0, true, 'none'));
    checks.push(check('Exchange rate', counts.exchangeRate.toFixed(2) + ' USD per EUR, adjusted ' + counts.adjustedExchangeRate.toFixed(2),
        counts.exchangeRate > PLAUSIBLE_EXCHANGE_RATE_MINIMUM && counts.exchangeRate < PLAUSIBLE_EXCHANGE_RATE_MAXIMUM,
        false, 'somewhere between 1 and 2.5'));
    checks.push(check('Cards without a price', formatNumber(counts.cardsWithoutPrice), true, false, ''));

    document.getElementById('sanityChecks').innerHTML = checks.map(entry =>
        '<li class="' + entry.className + '">' + escapeHtml(entry.label) + ': <strong>' + escapeHtml(String(entry.actual)) + '</strong>'
        + (entry.isPassing || entry.expectation === '' ? '' : ' <span class="text-body-secondary">expected ' + escapeHtml(entry.expectation) + '</span>')
        + '</li>').join('');

    const blocking = checks.filter(entry => entry.isBlocking && !entry.isPassing);
    hasBlockingSanityFailure = blocking.length > 0;

    const commitBlocked = document.getElementById('commitBlocked');
    commitBlocked.classList.toggle('d-none', !hasBlockingSanityFailure);
    if (hasBlockingSanityFailure) {
        document.getElementById('commitBlockedReason').innerHTML = 'Sanity check is off: '
            + escapeHtml(blocking.map(entry => entry.label.toLowerCase()).join(', '))
            + '. Usually MtgGoldfish changed their page or blocked half the requests --> prepare again.';
    }
}

function check(label, actual, isPassing, isBlocking, expectation) {
    let className = 'check-pass';
    if (!isPassing) className = isBlocking ? 'check-fail' : 'check-warn';

    return {
        label: label,
        actual: actual,
        isPassing: isPassing,
        isBlocking: isBlocking,
        expectation: expectation,
        className: className
    };
}

function isEveryFormatAt(rowCounts, expected) {
    return Object.keys(rowCounts).every(format => rowCounts[format] === expected);
}

function rowCountSummary(rowCounts, expected) {
    const offenders = Object.keys(rowCounts).filter(format => rowCounts[format] !== expected);
    if (offenders.length === 0) return expected + ' per format';

    return offenders.map(format => formatName(format) + ' ' + rowCounts[format]).join(', ');
}

function priceWindowDays(report) {
    const start = Date.parse(report.priceWindowStart + 'T00:00:00Z');
    const end = Date.parse(report.priceWindowEnd + 'T00:00:00Z');

    return Math.round((end - start) / (24 * 60 * 60 * 1000));
}

function renderReportSections(report) {
    const sections = [];

    sections.push(reportSection('New bans', report.newBans.length,
        'The news of the season.', banChangeTable(report.newBans)));
    sections.push(reportSection('Unbans', report.unbans.length, '', banChangeTable(report.unbans)));
    sections.push(reportSection('Newly extended', report.newExtended.length, '', banChangeTable(report.newExtended)));
    sections.push(reportSection('No longer extended', report.noLongerExtended.length, '', banChangeTable(report.noLongerExtended)));

    sections.push(reportSection('Biggest increases', report.topIncreases.length,
        'A card that jumps tenfold is usually one weird printing --> src/main/resources/IgnoredPrices.json, deploy, prepare again.',
        budgetPointTable(report.topIncreases)));
    sections.push(reportSection('Biggest decreases', report.topDecreases.length, '', budgetPointTable(report.topDecreases)));
    sections.push(reportSection('All relevant budget point changes', report.budgetPointChanges.length, '', budgetPointTable(report.budgetPointChanges)));

    sections.push(reportSection('Cards at zero budget points', report.zeroBudgetPointCards.length,
        'No price anywhere. A handful every season is normal.', budgetPointTable(report.zeroBudgetPointCards)));
    sections.push(reportSection('Missing cards', report.missingCards.length,
        'A card you know from the extension showing up here is the one to look at.', leftOutTable(report.missingCards)));
    sections.push(reportSection('Skipped cards', report.skippedCards.length,
        'Playtest cards, acorn cards and the like.', leftOutTable(report.skippedCards)));

    sections.push(reportSection('Oracle id changes', report.oracleIdChanges.length,
        'MTGJSON re-identified these. Commit applies them, nothing to do.', oracleIdTable(report.oracleIdChanges)));
    sections.push(reportSection('Renamed cards', report.renamedCards.length,
        'Commit applies them, nothing to do.', renamedTable(report.renamedCards)));
    sections.push(reportSection('Normalized name fixes', report.normalizedNameFixes.length,
        'Commit applies them, nothing to do.', renamedTable(report.normalizedNameFixes)));

    sections.push(reportSection('Sets that became playable', report.setsReleased.length,
        'For the Season History row and the announcement. Commander sets and promos are folded into their main set. '
        + 'MTGJSON lists the Collector’s Editions as decks of their own, so some show up twice - just skip those.',
        setTable(report.setsReleased)));

    document.getElementById('reportSections').innerHTML = sections.join('');
}

function reportSection(title, count, note, bodyHtml) {
    return '<details class="report-section mb-2">'
        + '<summary>' + escapeHtml(title) + ' <span class="text-body-secondary">' + count + '</span></summary>'
        + '<div class="mt-2 mb-4">'
        + (note === '' ? '' : '<p class="text-body-secondary">' + note + '</p>')
        + (count === 0 ? '<p class="text-body-secondary">None.</p>' : bodyHtml)
        + '</div>'
        + '</details>';
}

function dataTable(headers, rowsHtml) {
    return '<div class="card-list"><table class="table table-sm table-striped data-table">'
        + '<thead><tr>' + headers.map(header => '<th class="fw-normal text-body-secondary">' + header + '</th>').join('') + '</tr></thead>'
        + '<tbody>' + rowsHtml + '</tbody>'
        + '</table></div>';
}

function banChangeTable(cards) {
    const rows = cards.map(card => '<tr>'
        + '<td>' + cardLink(card.name) + '</td>'
        + '<td class="text-end">' + budgetPointText(card.budgetPoints) + '</td>'
        + '<td>' + escapeHtml(banReasons(card)) + '</td>'
        + '</tr>').join('');

    return dataTable(['Card', 'BP', 'Why'], rows);
}

// Same reasons as the ban list page, in the same order
function banReasons(card) {
    const reasons = [];
    const metaShares = metaShareText(card.metaShares);
    if (metaShares !== '') reasons.push(metaShares);
    if (card.bannedIn !== null) reasons.push('banned in ' + formatName(card.bannedIn));
    if (card.vintageRestricted) reasons.push('restricted in Vintage');

    return reasons.join(', ');
}

function budgetPointTable(cards) {
    const rows = cards.map(card => '<tr>'
        + '<td>' + cardLink(card.name) + '</td>'
        + '<td class="text-end">' + budgetPointText(card.previousBudgetPoints) + '</td>'
        + '<td class="text-end">' + budgetPointText(card.budgetPoints) + '</td>'
        + '<td class="text-end">' + (card.change > 0 ? '+' + formatNumber(card.change) : formatNumber(card.change)) + '</td>'
        + '</tr>').join('');

    return dataTable(['Card', 'Was', 'Now', 'Change'], rows);
}

function leftOutTable(cards) {
    const rows = cards.map(card => '<tr>'
        + '<td>' + cardLink(card.name) + '</td>'
        + '<td>' + escapeHtml(card.reason) + '</td>'
        + '<td class="text-body-secondary">' + escapeHtml(card.oracleId) + '</td>'
        + '</tr>').join('');

    return dataTable(['Card', 'Why', 'Oracle id'], rows);
}

function oracleIdTable(cards) {
    const rows = cards.map(card => '<tr>'
        + '<td>' + cardLink(card.name) + '</td>'
        + '<td class="text-body-secondary">' + escapeHtml(card.previousOracleId) + '</td>'
        + '<td class="text-body-secondary">' + escapeHtml(card.oracleId) + '</td>'
        + '<td>' + escapeHtml(card.firstSetCode) + '</td>'
        + '</tr>').join('');

    return dataTable(['Card', 'Was', 'Now', 'First set'], rows);
}

function renamedTable(cards) {
    const rows = cards.map(card => '<tr>'
        + '<td>' + escapeHtml(card.previousName) + '</td>'
        + '<td>' + cardLink(card.name) + '</td>'
        + '<td class="text-body-secondary">' + escapeHtml(card.previousNormalizedName) + '</td>'
        + '<td class="text-body-secondary">' + escapeHtml(card.normalizedName) + '</td>'
        + '</tr>').join('');

    return dataTable(['Was', 'Now', 'Normalized was', 'Normalized now'], rows);
}

function setTable(sets) {
    const rows = sets.map(mtgSet => '<tr>'
        + '<td>' + escapeHtml(mtgSet.name) + '</td>'
        + '<td>' + escapeHtml(mtgSet.code) + '</td>'
        + '<td>' + formatDate(mtgSet.releaseDate) + '</td>'
        + '<td>' + escapeHtml(mtgSet.type) + '</td>'
        + '<td class="text-end">' + formatNumber(mtgSet.newCardCount) + '</td>'
        + '<td>' + escapeHtml(joinOrEmpty(mtgSet.childCodes)) + '</td>'
        + '<td>' + escapeHtml(joinOrEmpty(mtgSet.commanderDecks)) + '</td>'
        + '</tr>').join('');

    return dataTable(['Set', 'Code', 'Released', 'Type', 'New cards', 'Also', 'Commander decks'], rows);
}

async function discardDraft() {
    if (!window.confirm('Throw the draft for season ' + draftReport.seasonNumber + ' away?')) return;

    const response = await request('./admin/v1/season/draft', {method: 'DELETE'});
    if (!response.ok) {
        showError('prepareError', await errorMessageOf(response));
        return;
    }

    await loadDraft(); // a DELETE hands back the last committed draft, if there is one
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
    renderCommitted(await response.json());
    await loadDraft(); // now it has committedAt --> the sql files get their final names
    await runLiveChecks();
}

function renderCommitted(committed) {
    const rows = [
        ['Season', committed.seasonNumber + ' (' + committed.romanSeasonNumber + ')'],
        ['Starts', formatDate(committed.startDate)],
        ['Finals Friday', formatDate(committed.finalsFriday)],
        ['Ends', formatDate(committed.endDate)],
        ['Next season starts', formatDate(committed.nextSeasonStart)],
        ['Cards inserted', formatNumber(committed.counts.insertedCards)],
        ['Card season data', formatNumber(committed.counts.upsertedCardSeasonData)],
        ['Cards remapped', formatNumber(committed.counts.remappedCards)],
        ['Names updated', formatNumber(committed.counts.updatedCardNames)]
    ];
    if (committed.pullRequest !== null && committed.pullRequest !== undefined) {
        rows.push(['Pull request', committed.pullRequest.url]);
    }

    document.getElementById('commitResult').innerHTML = '<div class="alert alert-success">Season '
        + committed.seasonNumber + ' is live.</div>'
        + '<table class="table table-sm data-table">'
        + rows.map(row => '<tr><th class="fw-normal text-body-secondary">' + row[0] + '</th><td>' + escapeHtml(row[1]) + '</td></tr>').join('')
        + '</table>';

    renderAnnouncement(committed, committed.newSets);
    document.getElementById('downloadWarning').classList.add('d-none');
}

async function runLiveChecks() {
    document.getElementById('liveChecks').classList.remove('d-none');

    let seasonLine = 'Could not read /legacy/season/current.';
    const seasonInfo = await loadCurrentSeason(); // the current season is another one now --> so are the defaults in step 1
    if (seasonInfo !== null) {
        seasonLine = 'Season ' + seasonInfo.seasonNumber + ', updated ' + formatDateTime(seasonInfo.updatedAt)
            + ' UTC - browser extensions reload their cache based on that timestamp.';
    }

    document.getElementById('liveCheckList').innerHTML = '<li>' + escapeHtml(seasonLine) + '</li>'
        + '<li><a href="./bans" target="_blank" rel="noreferrer">The ban list page</a> should show the new season.</li>';
}

async function reloadCardCache() {
    const response = await request('./admin/v1/cards/reload', {method: 'POST'});
    if (!response.ok) {
        showError('commitError', await errorMessageOf(response));
        return;
    }

    hideError('commitError');
    document.getElementById('reloadCacheButton').classList.add('d-none');
    await loadDraft();
    await runLiveChecks();
}

// Step 4 - the migrations

function renderDownloads(report) {
    document.getElementById('downloadWarning').classList.toggle('d-none', report.committedAt !== null);
    document.getElementById('downloadButtons').innerHTML = SQL_PARTS.map(part =>
        '<button type="button" class="btn btn-outline-primary btn-sm" data-sql-part="' + part + '">' + part + '</button>').join('');
    setOutcome('step4Outcome', '');
    renderPullRequest(report.pullRequestUrl);
}

function renderPullRequest(pullRequestUrl) {
    const hasPullRequest = pullRequestUrl !== null && pullRequestUrl !== undefined;
    document.getElementById('downloadInstructions').classList.toggle('d-none', hasPullRequest);
    document.getElementById('downloadPushHint').classList.toggle('d-none', hasPullRequest);
    document.getElementById('pullRequestResult').classList.toggle('d-none', !hasPullRequest);
    if (!hasPullRequest) return;

    const number = pullRequestUrl.substring(pullRequestUrl.lastIndexOf('/') + 1);
    document.getElementById('pullRequestResult').innerHTML = '<div class="alert alert-success">The three migrations are in '
        + '<a href="' + escapeHtml(pullRequestUrl) + '" target="_blank" rel="noreferrer">pull request #' + escapeHtml(number)
        + '</a>. Merge it, then run the deploy workflow. The downloads are still here if you want them anyway.</div>';
    setOutcome('step4Outcome', 'pull request #' + number + ' opened');
}

async function downloadSql(part) {
    hideError('downloadError');

    const response = await request('./admin/v1/season/draft/sql/' + part);
    if (!response.ok) {
        showError('downloadError', await errorMessageOf(response));
        return;
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
    setOutcome('step4Outcome', downloadedParts.length + ' of ' + SQL_PARTS.length + ' downloaded');
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

function renderSeasonHistory(report) {
    const setNames = report.setsReleased.map(mtgSet => mtgSet.name
        + (mtgSet.commanderDecks !== null && mtgSet.commanderDecks.length > 0 ? ' (+ Commander Decks)' : ''));
    const setCodes = [];
    report.setsReleased.forEach(mtgSet => {
        setCodes.push(mtgSet.code);
        if (mtgSet.childCodes !== null) mtgSet.childCodes.forEach(childCode => setCodes.push(childCode));
    });

    const cells = [
        report.seasonNumber,
        formatDocDate(report.startDate),
        formatDocDate(report.endDate),
        setNames.join('\n'),
        setCodes.join('\n'),
        '' // Updates, that one is yours
    ];

    document.getElementById('seasonHistoryTable').innerHTML = '<table class="table table-sm data-table">'
        + '<thead><tr>' + SEASON_HISTORY_COLUMNS.map(column => '<th class="fw-normal text-body-secondary">' + column + '</th>').join('') + '</tr></thead>'
        + '<tbody id="seasonHistoryRow"><tr>'
        + cells.map(cell => '<td style="white-space: normal;">' + escapeHtml(cell).replace(/\n/g, '<br>') + '</td>').join('')
        + '</tr></tbody>'
        + '</table>';
    setOutcome('step5Outcome', 'season ' + report.seasonNumber + ', ' + formatDate(report.startDate) + ' - ' + formatDate(report.endDate));
}

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

function renderScryfallDecks(decks) {
    const lists = [
        ['newBans', 'New bans', decks.newBans],
        ['unbans', 'Unbans', decks.unbans],
        ['currentBans', 'Current bans', decks.currentBans]
    ];

    document.getElementById('scryfallDecks').innerHTML = lists.map(list =>
        '<div class="mb-4">'
        + '<h3>' + list[1] + ' <span class="text-body-secondary">' + cardCountOf(list[2]) + '</span></h3>'
        + '<textarea class="form-control deck-list mb-2" id="deck-' + list[0] + '" readonly>' + escapeHtml(list[2]) + '</textarea>'
        + '<button type="button" class="btn btn-outline-secondary btn-sm" data-copy-target="deck-' + list[0] + '">Copy</button>'
        + '</div>').join('');

    setOutcome('step6Outcome', lists.map(list => cardCountOf(list[2])).join(' / '));
}

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
    document.getElementById('removalResult').innerHTML = '';
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

    removalPreview = await response.json();
    renderRemovalPreview(removalPreview);
}

function renderRemovalPreview(preview) {
    const rows = [
        ['Season', preview.seasonNumber + ', ' + formatDate(preview.startDate) + ' - ' + formatDate(preview.endDate)],
        ['Committed', preview.committedAt !== null ? formatDateTime(preview.committedAt) + ' by ' + preview.committedBy : 'not by this wizard'],
        ['Card season data', formatNumber(preview.cardSeasonDataRows) + ' rows'],
        ['Cards', formatNumber(preview.cardsAddedBySeason) + ' that no other season needs'],
        ['Goes back', preview.renamedCards + ' renames, ' + preview.oracleIdRemaps + ' oracle ids'],
        ['Previous season', preview.previousSeasonNumber !== null
            ? 'season ' + preview.previousSeasonNumber + ', ends ' + formatDate(preview.previousSeasonEndDate) + ' again'
            : 'not recorded'],
        ['Archive', preview.archiveDirectory]
    ];
    if (preview.pullRequestUrl !== null) rows.push(['Pull request', preview.pullRequestUrl]);

    const refusals = preview.refusals.length === 0 ? ''
        : '<div class="alert alert-warning">Season ' + preview.seasonNumber + ' stays:<ul class="mb-0">'
        + preview.refusals.map(refusal => '<li>' + escapeHtml(refusal) + '</li>').join('') + '</ul></div>';

    document.getElementById('removalPreview').innerHTML = refusals
        + '<table class="table table-sm data-table">'
        + rows.map(row => '<tr><th class="fw-normal text-body-secondary">' + row[0] + '</th><td>' + escapeHtml(row[1]) + '</td></tr>').join('')
        + '</table>';
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
    document.getElementById('removalButton').disabled = true;

    const githubToken = document.getElementById('removalGithubToken').value.trim();
    const response = await request('./admin/v1/season/' + removalPreview.seasonNumber + '/removal', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({githubToken: githubToken === '' ? null : githubToken})
    });
    if (!response.ok) {
        showError('removalError', await errorMessageOf(response));
        updateRemovalButton();
        return;
    }

    renderRemoved(await response.json());
}

function renderRemoved(removed) {
    const lines = [
        formatNumber(removed.counts.cardSeasonDataRows) + ' card season data rows gone',
        formatNumber(removed.counts.deletedCards) + ' cards gone',
        removed.counts.undoneRenames + ' renames and ' + removed.counts.undoneRemaps + ' oracle ids back where they were'
    ];
    if (removed.closedPullRequestUrl !== null) lines.push('branch deleted, ' + removed.closedPullRequestUrl + ' closed with it');
    if (removed.archiveDeleted) lines.push('the season folder in the archive is gone');

    document.getElementById('removalPreview').classList.add('d-none');
    document.getElementById('removalConfirm').classList.add('d-none');
    document.getElementById('removalGithubToken').value = '';
    document.getElementById('removalResult').innerHTML = '<div class="alert alert-success">Season ' + removed.seasonNumber
        + ' is gone. Season ' + removed.previousSeasonNumber + ' ends ' + escapeHtml(formatDate(removed.previousSeasonEndDate))
        + ' again. Reload the page before the next run.</div>'
        + '<ul>' + lines.map(line => '<li>' + escapeHtml(line) + '</li>').join('') + '</ul>';
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

function escapeHtml(text) {
    if (text === null || text === undefined) return '';

    const element = document.createElement('div');
    element.textContent = String(text);

    return element.innerHTML;
}

function cardLink(name) {
    return '<a href="https://scryfall.com/search?q=' + encodeURIComponent('!"' + name + '"')
        + '" target="_blank" rel="noreferrer">' + escapeHtml(name) + '</a>';
}

function joinOrEmpty(values) {
    return values === null || values === undefined ? '' : values.join(', ');
}

// Drafts from before the admin name was tracked have neither prepared_by nor committed_by
function byLine(name) {
    return name === null || name === undefined ? '' : ' by ' + name;
}

function budgetPointText(budgetPoints) {
    return budgetPoints === null || budgetPoints === undefined ? '-' : formatNumber(budgetPoints);
}

function metaShareText(metaShares) {
    if (metaShares === null || metaShares === undefined) return '';

    const parts = [];
    Object.keys(metaShares).forEach(format => {
        const share = metaShares[format];
        if (share === null) return;
        if (share === 0) { // MtgGoldfish rounds down to full percent --> the tail of the list sits at zero
            parts.push(formatName(format) + ' (< 1%)');
            return;
        }

        parts.push(formatName(format) + ' (' + Math.round(share * 100 * 100) / 100 + '%)');
    });

    return parts.join(', ');
}

function formatName(enumValue) {
    const lowercase = String(enumValue).toLowerCase().replace(/_/g, ' ');

    return lowercase.charAt(0).toUpperCase() + lowercase.substring(1);
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

// The Season History table writes them German style
function formatDocDate(isoDate) {
    const parts = isoDate.split('-');

    return parts[2] + '.' + parts[1] + '.' + parts[0];
}

function todayInUtc() {
    return new Date().toISOString().substring(0, 10);
}

function isoDatePlusDays(isoDate, days) {
    return new Date(Date.parse(isoDate + 'T00:00:00Z') + days * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);
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
