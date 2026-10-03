import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  BackHandler,
  StatusBar,
  StyleSheet,
  View,
  useColorScheme,
} from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useTracker } from './src/hooks/useTracker';
import { HomeScreen } from './src/screens/HomeScreen';
import { OnboardingScreen } from './src/screens/OnboardingScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { useTheme } from './src/theme';

type Route = 'home' | 'settings';

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <AppContent />
    </SafeAreaProvider>
  );
}

function AppContent() {
  const theme = useTheme();
  const { snapshot } = useTracker();
  const [route, setRoute] = useState<Route>('home');

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (route === 'settings') {
        setRoute('home');
        return true;
      }
      return false;
    });
    return () => sub.remove();
  }, [route]);

  if (!snapshot) {
    return (
      <View style={[styles.loading, { backgroundColor: theme.background }]}>
        <ActivityIndicator color={theme.signal} />
      </View>
    );
  }

  // The service can only be enabled by the user in system settings; until then, explain why
  // it is needed. useTracker re-checks when the user returns to the app.
  if (!snapshot.serviceEnabled) {
    return <OnboardingScreen />;
  }

  return route === 'settings' ? (
    <SettingsScreen snapshot={snapshot} onBack={() => setRoute('home')} />
  ) : (
    <HomeScreen snapshot={snapshot} onOpenSettings={() => setRoute('settings')} />
  );
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

export default App;
