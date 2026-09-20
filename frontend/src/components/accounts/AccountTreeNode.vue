<script setup lang="ts">
import { ref } from 'vue'
import AccountTreeNode from './AccountTreeNode.vue'
import { accountTypePillClass, formatMoney } from '@/lib/format'
import type { AccountNode } from '@/lib/types'

const props = defineProps<{
  node: AccountNode
  depth: number
  isAdmin: boolean
  /** When set, every node not on a path to a match is hidden. */
  filterTerm: string
}>()

defineEmits<{
  edit: [AccountNode]
  addChild: [AccountNode]
  archive: [AccountNode]
  restore: [AccountNode]
  remove: [AccountNode]
}>()

const expanded = ref(true)

function matches(node: AccountNode, term: string): boolean {
  const haystack = `${node.account.code} ${node.account.name}`.toLowerCase()
  if (haystack.includes(term)) return true
  return node.children.some((child) => matches(child, term))
}

const visible = () => !props.filterTerm || matches(props.node, props.filterTerm.toLowerCase())
</script>

<template>
  <template v-if="visible()">
    <tr :class="{ archived: node.account.archived }">
      <td>
        <div class="name-cell" :style="{ paddingLeft: `${depth * 20}px` }">
          <button
            v-if="node.children.length > 0"
            type="button"
            class="expander"
            :aria-label="expanded ? 'Collapse' : 'Expand'"
            @click="expanded = !expanded"
          >
            {{ expanded ? '▾' : '▸' }}
          </button>
          <span v-else class="expander-spacer" />
          <span class="mono code">{{ node.account.code }}</span>
          <RouterLink :to="`/accounts/${node.account.id}`">{{ node.account.name }}</RouterLink>
          <span v-if="!node.account.postable" class="pill pill-neutral">heading</span>
          <span v-if="node.account.systemRole" class="pill pill-neutral">{{ node.account.systemRole }}</span>
          <span v-if="node.account.archived" class="pill pill-amber">archived</span>
        </div>
      </td>
      <td>
        <span :class="accountTypePillClass(node.account.type)">{{ node.account.type }}</span>
      </td>
      <td class="mono">{{ node.account.currency }}</td>
      <td class="num mono">
        {{ node.account.postable ? formatMoney(node.account.balance, node.account.currency) : '—' }}
      </td>
      <td class="num mono">{{ formatMoney(node.account.rollupBalance, node.account.currency) }}</td>
      <td class="actions-cell">
        <template v-if="isAdmin">
          <button type="button" class="linkbtn" @click="$emit('edit', node)">Edit</button>
          <button type="button" class="linkbtn" @click="$emit('addChild', node)">+ Child</button>
          <button v-if="!node.account.archived" type="button" class="linkbtn" @click="$emit('archive', node)">
            Archive
          </button>
          <button v-else type="button" class="linkbtn" @click="$emit('restore', node)">Restore</button>
          <button type="button" class="linkbtn danger" @click="$emit('remove', node)">Delete</button>
        </template>
      </td>
    </tr>
    <template v-if="expanded">
      <AccountTreeNode
        v-for="child in node.children"
        :key="child.account.id"
        :node="child"
        :depth="depth + 1"
        :is-admin="isAdmin"
        :filter-term="filterTerm"
        @edit="(n) => $emit('edit', n)"
        @add-child="(n) => $emit('addChild', n)"
        @archive="(n) => $emit('archive', n)"
        @restore="(n) => $emit('restore', n)"
        @remove="(n) => $emit('remove', n)"
      />
    </template>
  </template>
</template>

<style scoped>
tr.archived {
  opacity: 0.6;
}
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.expander {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--ink-faint);
  width: 14px;
  font-size: 11px;
  padding: 0;
}
.expander-spacer {
  display: inline-block;
  width: 14px;
}
.code {
  color: var(--ink-faint);
  font-size: 12px;
}
.actions-cell {
  white-space: nowrap;
  text-align: right;
}
.linkbtn {
  background: none;
  border: none;
  color: var(--green);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.linkbtn:hover {
  text-decoration: underline;
}
.linkbtn.danger {
  color: var(--red);
}
</style>
