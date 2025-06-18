// This HAS to come first.  importScripts below will add a listener that prevents further propagation, so we have to nab it here.
addEventListener("notificationclick", (event) => {
    console.log("Click:", event);
    event.notification.close();

    const url = event.notification.data?.FCM_MSG?.data?.link ?? event.notification.data?.FCM_MSG?.notification?.click_action ?? event.notification.data?.link
    console.log("Event data: ", JSON.stringify(event.notification.data))
    if(typeof url !== "string") return
    console.log("Event url: ", url)

    // Taken from MDN
    event.waitUntil((async ()=> {
        const allClients = await clients.matchAll({
            includeUncontrolled: true,
        });

        let chatClient;

        // Let's see if we already have a chat window open:
        for (const client of allClients) {
            const url = new URL(client.url);
            console.log("Checking client ", client.url)
            if(client.url !== url) continue

            client.focus();
            chatClient = client;
            break
        }

        // If we didn't find an existing chat window,
        // open a new one:
        if (!chatClient) {
            chatClient = await clients.openWindow(url);
        }
    })())
});
console.log("Registered notificationclick")
// Give the service worker access to Firebase Messaging.
// Note that you can only use Firebase Messaging here. Other Firebase libraries
// are not available in the service worker.
importScripts("https://cdnjs.cloudflare.com/ajax/libs/firebase/10.7.1/firebase-app-compat.min.js");
importScripts("https://cdnjs.cloudflare.com/ajax/libs/firebase/10.7.1/firebase-messaging-compat.min.js");


firebase.initializeApp({
    apiKey: "REPLACE ME",
    authDomain: "REPLACE ME",
    projectId: "REPLACE ME",
    storageBucket: "REPLACE ME",
    messagingSenderId: "REPLACE ME",
    appId: "REPLACE ME",
});

// Retrieve an instance of Firebase Messaging so that it can handle background
// messages.
const messaging = firebase.messaging();

// If you would like to customize notifications that are received in the
// background (Web app is closed or not in browser focus) then you should
// implement this optional method.
// Keep in mind that FCM will still show notification messages automatically
// and you should use data messages for custom notifications.
// For more info see:
// https://firebase.google.com/docs/cloud-messaging/concept-options
messaging.onBackgroundMessage(function(payload) {
    // Customize notification here
    const notificationTitle = payload.notification.title;
    const notificationOptions = {
        body: payload.notification.body,
        icon: payload.notification.icon
    };

    self.registration.showNotification(notificationTitle, notificationOptions);
});