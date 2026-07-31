const previewAssets = [
  { kind: "领域约束", title: "身份格式", status: "已验证", detail: "规则、执行点和源码证据会在同一张卡片中展开。" },
  { kind: "领域事件", title: "业务事实", status: "已验证", detail: "展示生产者、消费者、关键字段与业务影响。" },
  { kind: "自动流程", title: "跨业务协同", status: "待接入运行态", detail: "以等待、下一步、超时和补偿解释流程。" }
];

const container = document.querySelector("#assets");
container.replaceChildren(...previewAssets.map((asset) => {
  const card = document.createElement("article");
  card.innerHTML = `
    <div class="kind">${asset.kind}</div>
    <h2>${asset.title}</h2>
    <p class="status">${asset.status}</p>
    <p>${asset.detail}</p>`;
  return card;
}));
