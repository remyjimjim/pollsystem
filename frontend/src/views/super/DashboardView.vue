<script setup lang="ts">
// Super Dashboard
</script>

<template>
  <div class="mx-auto max-w-4xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">Super Dashboard</h1>
    <p class="mb-6 text-slate-600">System-level controls.</p>

    <div class="grid gap-4 sm:grid-cols-[repeat(auto-fit,minmax(220px,1fr))]">
      <router-link
        to="/super/admin-requests"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">Admin Requests</h3>
        <p class="m-0 text-sm text-slate-600">
          Review and approve users requesting admin access.
        </p>
      </router-link>
      <router-link
        to="/super/manage-admins"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">Manage Admins</h3>
        <p class="m-0 text-sm text-slate-600">
          List current admins, toggle their zipcode assignments, demote.
        </p>
      </router-link>
      <router-link
        to="/super/poll-templates"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">Poll Templates</h3>
        <p class="m-0 text-sm text-slate-600">
          Edit the JSON template for each poll type.
        </p>
      </router-link>
      <router-link
        to="/super/ip-management"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">IP Allow / Deny</h3>
        <p class="m-0 text-sm text-slate-600">Manage IP allow / deny lists.</p>
      </router-link>
    </div>
  </div>
</template>
