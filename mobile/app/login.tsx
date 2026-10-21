import React, { useState } from 'react';
import { StyleSheet, View, Text, ScrollView, Alert } from 'react-native';
import { TextInput, Button, Card, Divider } from 'react-native-paper';
import { useRouter } from 'expo-router';
import { useAuth } from '../src/context/AuthContext';

export default function LoginScreen() {
  const router = useRouter();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleLogin = async (eEmail?: string, ePassword?: string) => {
    const targetEmail = eEmail || email;
    const targetPassword = ePassword || password;

    if (!targetEmail || !targetPassword) {
      setErrorMessage('Please enter both email and password');
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      const res = await login(targetEmail.trim(), targetPassword);
      if (res.role === 'ROLE_DRIVER') {
        router.replace('/driver');
      } else {
        router.replace('/passenger');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Login failed';
      setErrorMessage(msg);
      Alert.alert('Login Error', msg);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickLogin = async (role: 'PASSENGER' | 'DRIVER') => {
    const targetEmail = role === 'PASSENGER' ? 'passenger@juniv.edu' : 'driver1@juniv.edu';
    const targetPass = 'password123';
    setEmail(targetEmail);
    setPassword(targetPass);
    await handleLogin(targetEmail, targetPass);
  };

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Card style={styles.card}>
        <Card.Content>
          <Text style={styles.title}>Welcome Back</Text>
          <Text style={styles.subtitle}>Sign in with your JU campus credentials</Text>

          {errorMessage ? (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{errorMessage}</Text>
            </View>
          ) : null}

          <TextInput
            label="JU Email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
            style={styles.input}
            mode="outlined"
            activeOutlineColor="#16a34a"
          />

          <TextInput
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            style={styles.input}
            mode="outlined"
            activeOutlineColor="#16a34a"
          />

          <Button
            mode="contained"
            onPress={() => handleLogin()}
            loading={loading}
            disabled={loading}
            style={styles.loginBtn}
          >
            Sign In
          </Button>

          <View style={styles.dividerContainer}>
            <Divider style={styles.divider} />
            <Text style={styles.dividerLabel}>OR QUICK DEMO LOGIN</Text>
            <Divider style={styles.divider} />
          </View>

          <Button
            mode="contained-tonal"
            icon="account"
            onPress={() => handleQuickLogin('PASSENGER')}
            style={styles.demoBtn}
            textColor="#166534"
          >
            🎓 Login as Demo Passenger (Student)
          </Button>

          <Button
            mode="contained-tonal"
            icon="car"
            onPress={() => handleQuickLogin('DRIVER')}
            style={[styles.demoBtn, { marginTop: 8 }]}
            textColor="#1e3a8a"
          >
            🛺 Login as Demo Driver (Rickshaw Mama)
          </Button>

          <View style={styles.registerLink}>
            <Text style={styles.registerText}>Don't have an account? </Text>
            <Button
              compact
              mode="text"
              textColor="#16a34a"
              onPress={() => router.push('/register')}
            >
              Register here
            </Button>
          </View>
        </Card.Content>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    padding: 20,
    backgroundColor: '#f8fafc',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    paddingVertical: 10,
    elevation: 3,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#0f172a',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: '#64748b',
    marginBottom: 20,
  },
  errorBox: {
    backgroundColor: '#fee2e2',
    padding: 10,
    borderRadius: 8,
    marginBottom: 16,
  },
  errorText: {
    color: '#991b1b',
    fontSize: 13,
  },
  input: {
    marginBottom: 14,
    backgroundColor: '#ffffff',
  },
  loginBtn: {
    backgroundColor: '#16a34a',
    borderRadius: 8,
    marginTop: 8,
    paddingVertical: 4,
  },
  dividerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 20,
  },
  divider: {
    flex: 1,
    backgroundColor: '#e2e8f0',
  },
  dividerLabel: {
    marginHorizontal: 10,
    fontSize: 10,
    fontWeight: 'bold',
    color: '#94a3b8',
  },
  demoBtn: {
    borderRadius: 8,
    backgroundColor: '#f1f5f9',
  },
  registerLink: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 20,
  },
  registerText: {
    fontSize: 13,
    color: '#64748b',
  },
});
