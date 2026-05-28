const admin = require("../functions/node_modules/firebase-admin");

admin.initializeApp({ projectId: "octalink-28088" });
const db = admin.firestore();

(async () => {
  const snap = await db.collection("members")
    .where("aiRoutineBeta", "==", true)
    .get();
  console.log(`aiRoutineBeta=true grants: ${snap.size}/12`);
  snap.docs.forEach((d) => {
    const data = d.data();
    const created = data.createdAt?.toDate?.()?.toISOString?.() ?? "<no createdAt>";
    console.log(`  - ${d.id}  name=${data.name ?? "?"}  role=${data.role ?? "?"}  createdAt=${created}`);
  });
  process.exit(0);
})();
