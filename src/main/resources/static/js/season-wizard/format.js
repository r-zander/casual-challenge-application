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
const GERMAN_MONTH_NAMES = [
    'Jan',
    'Feb',
    'Mär',
    'Apr',
    'Mai',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Okt',
    'Nov',
    'Dez'
];
// Seasons will not reach 90 before any of us is retired
/** @type {[number, string][]} */
const ROMAN_NUMERALS = [[50, 'L'], [40, 'XL'], [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']];

// Drafts from before the admin name was tracked have neither prepared_by nor committed_by
/**
 * @param {string | null} name
 * @returns {string}
 */
export function byLine(name) {
    return name === null || name === undefined ? '' : ' by ' + name;
}

/**
 * @param {number | null} value
 * @returns {string}
 */
export function formatNumber(value) {
    if (value === null || value === undefined) return '';

    return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/**
 * @param {number | null} value
 * @returns {string}
 */
export function formatGermanNumber(value) {
    if (value === null || value === undefined) return '';

    return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/**
 * @param {string | null} isoDate
 * @returns {string}
 */
export function formatDate(isoDate) {
    if (isoDate === null || isoDate === undefined) return '';

    const parts = isoDate.split('-');

    return Number(parts[2]) + ' ' + MONTH_NAMES[Number(parts[1]) - 1] + ' ' + parts[0];
}

/**
 * @param {string | null} isoDate
 * @returns {string}
 */
export function formatGermanDate(isoDate) {
    if (isoDate === null || isoDate === undefined) return '';

    const parts = isoDate.split('-');

    return Number(parts[2]) + '. ' + GERMAN_MONTH_NAMES[Number(parts[1]) - 1] + ' ' + parts[0];
}

/**
 * @returns {string}
 */
export function todayInUtc() {
    return new Date().toISOString().substring(0, 10);
}

/**
 * @param {string} isoDate
 * @param {number} days
 * @returns {string}
 */
export function isoDatePlusDays(isoDate, days) {
    return new Date(Date.parse(isoDate + 'T00:00:00Z') + days * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);
}

/**
 * @param {string} fromIsoDate
 * @param {string} toIsoDate
 * @returns {number}
 */
export function daysBetween(fromIsoDate, toIsoDate) {
    return Math.round((Date.parse(toIsoDate + 'T00:00:00Z') - Date.parse(fromIsoDate + 'T00:00:00Z')) / (24 * 60 * 60 * 1000));
}

/**
 * @param {number} days
 * @returns {string}
 */
function dayText(days) {
    return days + (days === 1 ? ' day' : ' days');
}

/**
 * @param {number} days
 * @returns {string}
 */
export function agoText(days) {
    if (days === 0) return 'today';
    if (days === 1) return 'yesterday';

    return days + ' days ago';
}

/**
 * @param {number} days
 * @returns {string}
 */
export function endsText(days) {
    if (days < 0) return agoText(-days);
    if (days === 0) return 'today';

    return 'in ' + dayText(days);
}

/**
 * @param {number} days
 * @returns {string}
 */
export function lengthText(days) {
    if (days < 14) return dayText(days);

    const weeks = Math.floor(days / 7);
    const remainingDays = days % 7;

    return weeks + ' weeks' + (remainingDays === 0 ? '' : ' and ' + dayText(remainingDays));
}

/**
 * @param {number} seasonNumber
 * @returns {string}
 */
export function romanNumeral(seasonNumber) {
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

/**
 * @param {string | null} isoDateTime
 * @returns {string}
 */
export function formatDateTime(isoDateTime) {
    if (isoDateTime === null || isoDateTime === undefined) return '';

    const parts = isoDateTime.split('T');

    return formatDate(parts[0]) + ', ' + parts[1].substring(0, 5);
}

/**
 * @param {number} milliseconds
 * @returns {string}
 */
export function formatTimestamp(milliseconds) {
    const date = new Date(milliseconds);

    return date.getDate() + ' ' + MONTH_NAMES[date.getMonth()] + ' ' + date.getFullYear();
}

/**
 * @param {string | null} deckList
 * @returns {string}
 */
export function cardCountOf(deckList) {
    if (deckList === null || deckList === undefined || deckList.trim() === '') return '0 cards';

    return deckList.trim().split('\n').length + ' cards';
}
