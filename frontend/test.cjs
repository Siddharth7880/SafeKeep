const axios = require('axios');

async function run() {
  try {
    // 1. Register a user
    const email = 'test' + Date.now() + '@example.com';
    const pw = 'Password@123';
    await axios.post('http://localhost:8080/api/auth/register', {
      fullName: 'Test User', email: email, password: pw
    });
    // 2. Login
    const loginRes = await axios.post('http://localhost:8080/api/auth/login', {
      email: email, password: pw
    });
    const token = loginRes.data.data.accessToken || loginRes.data.data.token;
    console.log('Logged in, token:', token ? token.substring(0, 10) + '...' : loginRes.data);

    const api = axios.create({
      baseURL: 'http://localhost:8080',
      headers: { Authorization: 'Bearer ' + token }
    });

    // 3. Create Vault Item
    const vaultPw = 'vault123';
    const formData = new FormData();
    formData.append('label', 'Test Item');
    formData.append('contentType', 'DOCUMENT');
    formData.append('content', 'This is a secret message.');

    const createRes = await api.post('/api/vault/items', {
      label: 'Test Item',
      contentType: 'DOCUMENT',
      content: 'This is a secret message.'
    }, {
      headers: { 'X-Vault-Password': vaultPw }
    });
    const itemId = createRes.data.data.id;
    console.log('Created item:', itemId);

    // 4. Fetch the item
    const getRes = await api.get('/api/vault/items/' + itemId, {
      headers: { 'X-Vault-Password': vaultPw }
    });
    console.log('Decrypted Content:', getRes.data.data.content);

  } catch (e) {
    console.error('Error:', e.response ? e.response.data : e.message);
  }
}
run();
