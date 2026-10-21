import React, { useState } from 'react';
import { StyleSheet, View, Text, ScrollView, Alert } from 'react-native';
import { TextInput, Button, Card, SegmentedButtons } from 'react-native-paper';
import { useRouter } from 'expo-router';
import { useAuth } from '../src/context/AuthContext';

export default function RegisterScreen() {
  const router = useRouter();
  const { register } = useAuth();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'ROLE_PASSENGER' | 'ROLE_DRIVER'>('ROLE_PASSENGER');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleRegister = async () => {
    if (!fullName || !email || !password) {
      setErrorMessage('Full name, email, and password are required');
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      const res = await register({
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        password,
        role,
      });

      if (res.role === 'ROLE_DRIVER') {
        router.replace('/driver');
      } else {
        router.replace('/passenger');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Registration failed';
      setErrorMessage(msg);
      Alert.alert('Registration Error', msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Card style={styles.card}>
        <Card.Content>
          <Text style={styles.title}>Join JU Smart Ride</Text>
          <Text style={styles.subtitle}>Create your campus commuter or driver profile</Text>

          {errorMessage ? (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{errorMessage}</Text>
            </View>
          ) : null}

          <Text style={styles.roleLabel}>I want to join as:</Text>
          <SegmentedButtons
            value={role}
            onValueChange={(val) => setRole(val as any)}
            buttons={[
              { value: 'ROLE_PASSENGER', label: '🎓 Student / Passenger' },
              { value: 'ROLE_DRIVER', label: '🛺 Rickshaw Driver' },
            ]}
            style={styles.segmented}
          />

          <TextInput
            label="Full Name"
            value={fullName}
            onChangeText={setFullName}
            style={styles.input}
            mode="outlined"
            activeOutlineColor="#16a34a"
          />

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
            label="Mobile Phone (+880)"
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
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
            onPress={handleRegister}
            loading={loading}
            disabled={loading}
            style={styles.registerBtn}
          >
            Create Account
          </Button>

          <Button
            mode="text"
            textColor="#64748b"
            onPress={() => router.back()}
            style={{ marginTop: 12 }}
          >
            Already have an account? Sign In
          </Button>
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
    marginBottom: 16,
  },
  roleLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#334155',
    marginBottom: 8,
  },
  segmented: {
    marginBottom: 16,
  },
  errorBox: {
    backgroundColor: '#fee2e2',
    padding: 10,
    borderRadius: 8,
    marginBottom: 14,
  },
  errorText: {
    color: '#991b1b',
    fontSize: 13,
  },
  input: {
    marginBottom: 14,
    backgroundColor: '#ffffff',
  },
  registerBtn: {
    backgroundColor: '#16a34a',
    borderRadius: 8,
    marginTop: 8,
    paddingVertical: 4,
  },
});
