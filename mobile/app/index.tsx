import React, { useEffect } from 'react';
import { StyleSheet, View, Text, Image, ScrollView } from 'react-native';
import { Button, Card, Title, Paragraph } from 'react-native-paper';
import { useRouter } from 'expo-router';
import { useAuth } from '../src/context/AuthContext';

export default function IndexScreen() {
  const router = useRouter();
  const { user, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && user) {
      if (user.role === 'ROLE_DRIVER') {
        router.replace('/driver');
      } else {
        router.replace('/passenger');
      }
    }
  }, [user, isLoading]);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.header}>
        <Text style={styles.badge}>Jahangirnagar University</Text>
        <Text style={styles.title}>JU Smart Ride</Text>
        <Text style={styles.subtitle}>
          Campus Rickshaw Sharing & Java DSA Shortest-Path Route Optimization
        </Text>
      </View>

      <Card style={styles.card}>
        <Card.Content>
          <Title style={styles.cardTitle}>🎓 For Students & Faculty</Title>
          <Paragraph style={styles.cardDesc}>
            Find available campus rickshaws, share rides with classmates, compare Dijkstra-optimized routes, and avoid high detour rates.
          </Paragraph>
          <Button
            mode="contained"
            style={styles.btnPrimary}
            onPress={() => router.push('/login')}
          >
            Sign In / Get Started
          </Button>
        </Card.Content>
      </Card>

      <Card style={[styles.card, styles.driverCard]}>
        <Card.Content>
          <Title style={styles.cardTitle}>🛺 For Campus Rickshaw Pullers</Title>
          <Paragraph style={styles.cardDesc}>
            Set stand availability, accept matched requests with seat capacity limits (max 3), and navigate campus safely.
          </Paragraph>
          <Button
            mode="outlined"
            textColor="#16a34a"
            style={styles.btnSecondary}
            onPress={() => router.push('/login')}
          >
            Driver Portal
          </Button>
        </Card.Content>
      </Card>

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          Powered by Java Spring Boot, Dijkstra Shortest-Path & Trie Search
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#f8fafc',
    padding: 20,
    justifyContent: 'center',
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
    marginTop: 20,
  },
  badge: {
    backgroundColor: '#dcfce7',
    color: '#166534',
    paddingVertical: 4,
    paddingHorizontal: 12,
    borderRadius: 20,
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 10,
    textTransform: 'uppercase',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#0f172a',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#64748b',
    textAlign: 'center',
    marginTop: 8,
    paddingHorizontal: 10,
    lineHeight: 20,
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    marginBottom: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowOffset: { width: 0, height: 2 },
  },
  driverCard: {
    borderColor: '#bbf7d0',
    borderWidth: 1,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1e293b',
    marginBottom: 6,
  },
  cardDesc: {
    fontSize: 13,
    color: '#64748b',
    marginBottom: 16,
    lineHeight: 18,
  },
  btnPrimary: {
    backgroundColor: '#16a34a',
    borderRadius: 8,
    paddingVertical: 4,
  },
  btnSecondary: {
    borderColor: '#16a34a',
    borderRadius: 8,
    paddingVertical: 4,
  },
  footer: {
    marginTop: 20,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 11,
    color: '#94a3b8',
    textAlign: 'center',
  },
});
