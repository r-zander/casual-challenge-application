import {state} from './state.js';

/** @type {((reason: string) => void) | null} */
let unauthorizedHandler = null;

/**
 * @param {(reason: string) => void} handler
 */
export function setUnauthorizedHandler(handler) {
    unauthorizedHandler = handler;
}

// Requests

/**
 * @param {string} path
 * @param {RequestInit} [options]
 * @returns {Promise<Response>}
 */
export async function request(path, options) {
    /** @type {RequestInit} */
    const requestOptions = options === undefined ? {} : options;
    requestOptions.headers = Object.assign({}, requestOptions.headers, {'Authorization': 'Bearer ' + state.adminToken});

    const response = await fetch(path, requestOptions);
    if (response.status === 401) {
        unauthorizedHandler('Token stopped working - expired, or the JWT secret was rotated.');
    }

    return response;
}

/**
 * @param {Response} response
 * @returns {Promise<string>}
 */
export async function errorMessageOf(response) {
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
