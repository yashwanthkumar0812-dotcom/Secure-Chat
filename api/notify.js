const admin = require('firebase-admin');
if (!admin.apps.length) {
    admin.initializeApp({ credential: admin.credential.cert({ projectId: process.env.FIREBASE_PROJECT_ID, clientEmail: process.env.FIREBASE_CLIENT_EMAIL, privateKey: process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n') }) });
}
export default async function handler(req, res) {
    if (req.method !== 'POST') return res.status(405).send('Method Not Allowed');
    try {
        await admin.messaging().send({ token: req.body.token, notification: { title: req.body.title, body: req.body.body }, data: { messageId: req.body.messageId } });
        res.status(200).json({ success: true });
    } catch (error) { res.status(500).json({ error: error.message }); }
}
