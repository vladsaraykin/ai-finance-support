import test from 'node:test';
import assert from 'node:assert/strict';
import { newRequestId } from '../src/request-id.js';

test('uses native UUID when available', () => {
  assert.equal(newRequestId({ randomUUID: () => 'native-id' }), 'native-id');
});

test('HTTP without randomUUID produces a valid UUID v4 using random bytes', () => {
  const source = { getRandomValues: (bytes) => bytes.fill(255) };
  assert.equal(newRequestId(source), 'ffffffff-ffff-4fff-bfff-ffffffffffff');
  const randomOnly = { getRandomValues: (bytes) => globalThis.crypto.getRandomValues(bytes) };
  const first = newRequestId(randomOnly);
  assert.match(first, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.notEqual(first, newRequestId(randomOnly));
});
