/**
 * @format
 */

import React from 'react';
import { DeviceEventEmitter, Text } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';
import NativeReelsTracker from '../src/specs/NativeReelsTracker';

const getSnapshot = NativeReelsTracker.getSnapshot as jest.Mock;
const baseSnapshot = (jest.requireMock('../src/specs/NativeReelsTracker') as any)
  .__snapshot;

const mounted: ReactTestRenderer.ReactTestRenderer[] = [];

async function render() {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<App />);
  });
  mounted.push(renderer);
  return renderer;
}

function textContent(renderer: ReactTestRenderer.ReactTestRenderer) {
  return renderer.root
    .findAllByType(Text)
    .map(n => [n.props.children].flat().join(''))
    .join('\n');
}

function counterLabel(renderer: ReactTestRenderer.ReactTestRenderer) {
  return renderer.root.find(
    n =>
      typeof n.props.accessibilityLabel === 'string' &&
      n.props.accessibilityLabel.endsWith('swiped today'),
  ).props.accessibilityLabel;
}

afterEach(async () => {
  await ReactTestRenderer.act(async () => {
    mounted.splice(0).forEach(r => r.unmount());
  });
  getSnapshot.mockImplementation(() => Promise.resolve({ ...baseSnapshot }));
});

test('shows onboarding when the accessibility service is disabled', async () => {
  getSnapshot.mockResolvedValueOnce({ ...baseSnapshot, serviceEnabled: false });
  const renderer = await render();
  expect(textContent(renderer)).toContain('Enable Reel tracking');
});

test('shows the dashboard and updates on reelScrollDetected', async () => {
  getSnapshot.mockResolvedValueOnce({
    ...baseSnapshot,
    todayCount: 126,
    sessionCount: 41,
    inReels: true,
    inInstagram: true,
  });
  const renderer = await render();
  let text = textContent(renderer);
  expect(text).toContain('Reels Counter');
  expect(text).toContain('Active');
  expect(text).toContain('Counting your swipes in Reels');
  expect(counterLabel(renderer)).toBe('126 Reels swiped today');

  await ReactTestRenderer.act(async () => {
    DeviceEventEmitter.emit('reelScrollDetected', {
      count: 127,
      sessionCount: 42,
      timestamp: Date.now(),
    });
  });
  text = textContent(renderer);
  expect(counterLabel(renderer)).toBe('127 Reels swiped today');
  expect(text).toContain('42');
  expect(text).toContain('Just now');
});

test('reflects trackingStatusChanged events', async () => {
  const renderer = await render();
  await ReactTestRenderer.act(async () => {
    DeviceEventEmitter.emit('trackingStatusChanged', {
      ...baseSnapshot,
      trackingEnabled: false,
    });
  });
  const text = textContent(renderer);
  expect(text).toContain('Inactive');
  expect(text).toContain('Paused');
  expect(text).toContain('Resume counting');
});
