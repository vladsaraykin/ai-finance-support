import test from 'node:test';
import assert from 'node:assert/strict';
import { dateInputValue } from '../src/finance-format.js';

test('editor shows the Moscow calendar date when PostgreSQL returns a UTC timestamp', () => {
  assert.equal(dateInputValue('2026-10-31T22:30:00Z'), '2026-11-01');
  assert.equal(dateInputValue('2026-11-01T01:30:00+03:00'), '2026-11-01');
  assert.equal(dateInputValue(null), '');
});
