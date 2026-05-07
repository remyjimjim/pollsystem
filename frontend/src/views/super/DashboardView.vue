<script setup lang="ts">
// Super Dashboard
</script>

<template>
  <div class="view">
    <h1>Super Dashboard</h1>
    <p class="lead">System-level controls.</p>

    <div class="cards">
      <router-link to="/super/admin-requests" class="card">
        <h3>Admin Requests</h3>
        <p>Review and approve users requesting admin access.</p>
      </router-link>
      <router-link to="/super/manage-admins" class="card">
        <h3>Manage Admins</h3>
        <p>List current admins, toggle their zipcode assignments, demote.</p>
      </router-link>
      <router-link to="/super/poll-templates" class="card">
        <h3>Poll Templates</h3>
        <p>Edit the JSON template for each poll type.</p>
      </router-link>
      <router-link to="/super/ip-management" class="card">
        <h3>IP Allow / Deny</h3>
        <p>Manage IP allow / deny lists.</p>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 900px; margin: 0 auto; }
h1 { color: #1a365d; margin-bottom: 0.5rem; }
.lead { color: #4a5568; margin-bottom: 1.5rem; }
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}
.card {
  display: block;
  padding: 1.25rem;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: white;
  text-decoration: none;
  color: inherit;
  transition: border-color 120ms;
}
.card:hover { border-color: #1a365d; }
.card h3 { margin: 0 0 0.5rem; color: #1a365d; }
.card p { margin: 0; color: #4a5568; font-size: 0.9rem; }
</style>
