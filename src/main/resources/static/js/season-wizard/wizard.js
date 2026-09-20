import {COMMIT_RESULT_STORAGE_KEY, DONE_STEPS_STORAGE_KEY, OPEN_STEP_STORAGE_KEY, TOKEN_STORAGE_KEY, state} from './state.js';
import {errorMessageOf, setUnauthorizedHandler} from './api.js';
import {completeStep, flashCopied, hideError, inputOf, openStep, restoreOpenStep, setOutcome, setStepDone, showError} from './steps.js';
import {formatTimestamp} from './format.js';
import {cancelPreparation, loadCurrentSeason, metaSourceValue, renderPreparationDefaults, renderPreparationStatus, setPreparationDoneHandler, startPreparation, stopPolling} from './preparation.js';
import {commitSeason, reloadCardCache, updateCommitButton, updateLiveChecks} from './commit.js';
import {downloadAllSql, downloadSql} from './migrations.js';
import {loadRemovalPreview, removeSeason, updateRemovalButton} from './removal.js';
import {copySeasonHistoryRow, discardDraft, loadDraft} from './draft.js';

/** @typedef {import('./types.js').TokenPayload} TokenPayload */

// Step 0 - the token

/**
 * @param {string} token
 * @returns {TokenPayload | null}
 */
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

/**
 * @returns {Promise<void>}
 */
async function submitToken() {
    const token = inputOf('tokenInput').value.trim();
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

    state.adminToken = token;
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

/**
 * @param {string} [reason]
 */
function forgetToken(reason) {
    stopPolling();
    sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    sessionStorage.removeItem(DONE_STEPS_STORAGE_KEY);
    sessionStorage.removeItem(COMMIT_RESULT_STORAGE_KEY);
    sessionStorage.removeItem(OPEN_STEP_STORAGE_KEY);
    state.adminToken = null;
    state.draftReport = null;
    state.doneSteps = [];

    document.getElementById('wizard').classList.add('d-none');
    document.getElementById('tokenGate').classList.remove('d-none');
    inputOf('tokenInput').value = '';
    if (reason !== undefined) showError('tokenError', reason);
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
        sessionStorage.setItem(OPEN_STEP_STORAGE_KEY, /** @type {HTMLElement} */ (event.target).id);
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
    document.getElementById('commitButton').addEventListener('click', async () => {
        if (await commitSeason()) await loadDraft(); // now it has committedAt --> the sql files get their final names, and the result comes back out of the storage
    });
    document.getElementById('reloadCacheButton').addEventListener('click', async () => {
        if (await reloadCardCache()) await loadDraft(); // the draft is committed in this one, so the verification comes with it
    });
    document.getElementById('liveChecks').addEventListener('change', updateLiveChecks);
    document.getElementById('downloadAllButton').addEventListener('click', downloadAllSql);

    document.addEventListener('click', async event => {
        const clickTarget = /** @type {Element} */ (event.target);

        const downloadButton = /** @type {HTMLElement} */ (clickTarget.closest('[data-sql-part]'));
        if (downloadButton !== null) {
            await downloadSql(downloadButton.dataset.sqlPart);
            return;
        }

        const doneButton = /** @type {HTMLElement} */ (clickTarget.closest('[data-done-step]'));
        if (doneButton !== null) {
            completeStep(Number(doneButton.dataset.doneStep));
            return;
        }

        const copyRowButton = /** @type {HTMLElement} */ (clickTarget.closest('[data-copy-html]'));
        if (copyRowButton !== null) {
            await copySeasonHistoryRow(copyRowButton.dataset.copyHtml);
            flashCopied(copyRowButton);
            return;
        }

        const copyButton = /** @type {HTMLElement} */ (clickTarget.closest('[data-copy-target]'));
        if (copyButton === null) return;

        const source = /** @type {HTMLTextAreaElement} */ (document.getElementById(copyButton.dataset.copyTarget));
        await navigator.clipboard.writeText(source.value !== undefined ? source.value : source.textContent);
        flashCopied(copyButton);
    });
}

setUnauthorizedHandler(forgetToken);
setPreparationDoneHandler(async () => {
    await loadDraft();
    openStep('step2Body');
});

wireUpControls();

const storedToken = sessionStorage.getItem(TOKEN_STORAGE_KEY);
if (storedToken !== null) {
    inputOf('tokenInput').value = storedToken;
    // noinspection JSIgnoredPromiseFromCall
    submitToken();
}
