# Instalação — Metalúrgica Fantineli

Roteiro para instalar o sistema no computador do escritório. O sistema fica
rodando **nessa máquina**, e os outros micros acessam pelo navegador, pela rede
da oficina.

Leve esta pasta inteira num pendrive.

---

## Antes de ir

Confira na máquina do cliente, de preferência antes do dia:

- **Windows 10 ou 11**
- Espaço em disco: 2 GB folgados
- A máquina **fica ligada** durante o expediente (é ela que serve os outros)
- Você consegue entrar como **administrador** nela

---

## Passo a passo

### 1. Copie a pasta

Copie `pacote-instalacao` do pendrive para o disco, por exemplo em
`C:\Fantineli`. **Não rode a partir do pendrive** — o sistema vai viver aqui.

### 2. Instale o Java

Se ainda não tiver: <https://adoptium.net/temurin/releases/?version=21>

Escolha **Windows · x64 · JRE · .msi**. Instalar é next-next-finish.

### 3. Instale o PostgreSQL

<https://www.postgresql.org/download/windows/> — versão **16**.

Durante a instalação:

- **Anote a senha do usuário `postgres`.** Você vai precisar dela no passo
  seguinte, e o cliente vai precisar dela se um dia trocar de máquina.
- Deixe a porta em **5432**
- Pode desmarcar o *Stack Builder* no final

### 4. Rode o instalador

Clique com o botão direito em `instalar.ps1` → **Executar com o PowerShell**.

> Se aparecer aviso de execução de scripts, abra o PowerShell **como
> administrador** e rode:
> `Set-ExecutionPolicy -Scope Process Bypass` e depois `.\instalar.ps1`

Rodar **como administrador** importa: é o que permite liberar a porta no
firewall para os outros micros do escritório.

O script vai pedir:

- a senha do `postgres` (a do passo 3)
- login, senha e nome do **administrador do sistema** — este é o acesso do Leo

E vai cuidar sozinho de: criar o banco, gerar a chave de segurança, liberar o
firewall e fazer o sistema subir junto com o Windows.

### 5. Ligue

Clique duas vezes em **`iniciar.bat`**.

A primeira vez demora mais — o sistema está criando as tabelas. Quando aparecer
`Started EstoqueApplication`, está no ar.

Abra o navegador em **<http://localhost:8080>** e entre com o login criado no
passo 4.

### 6. Descubra o endereço para os outros micros

O instalador mostra no final, algo como `http://192.168.0.15:8080`.

Teste de outro computador do escritório antes de ir embora. Se não abrir:

- confira se os dois estão na **mesma rede**
- confira se o firewall foi liberado (rodou como administrador?)
- confira se `iniciar.bat` está rodando na máquina principal

Deixe esse endereço anotado num papel colado no monitor, ou salve como favorito
nos navegadores do escritório.

---

## Depois de instalado

### Ligar e desligar

O sistema **sobe sozinho** quando o computador liga. A janela preta que aparece
é ele rodando — **não feche**, ou o sistema desliga para todo mundo.

Se precisar reiniciar: feche a janela e clique em `iniciar.bat` de novo.

### Backup — não pule esta parte

Clique em **`backup.bat`**. Ele salva uma cópia do banco na pasta `backups`.

Isso protege contra erro de digitação e apagão, **mas não contra o computador
queimar**. Oriente o cliente a copiar a pasta `backups` para um pendrive ou
para o Google Drive de vez em quando.

Melhor ainda: agende. Abra o **Agendador de Tarefas** do Windows, crie uma
tarefa diária apontando para `backup.bat`. Cinco minutos, e resolve o risco de
perder o histórico do negócio.

### Criar os outros acessos

Entre como administrador, vá em **Usuários** e crie os logins do escritório.
São três perfis:

| Perfil | Para quem | Vê o lucro? | Cria usuários? |
|---|---|---|---|
| **Administrador** | os sócios | sim | sim |
| **Escritório** | quem faz orçamento | sim | não |
| **Operador** | chão de fábrica | **não** | não |

O operador registra material e corte normalmente — o que ele não enxerga é
preço de venda e margem.

### Cadastrar as chapas

Para a aba **Cortes** funcionar, cada chapa precisa ter em **Produtos**, na
seção *Parâmetros de corte a laser*:

- largura e comprimento da chapa (mm)
- valor da chapa inteira
- preço por metro de corte

Sem isso a chapa não aparece para orçamento.

---

## Se der problema

**"Porta 8080 já em uso"** — outro programa ocupou a porta. Reinicie a máquina,
ou edite `iniciar.bat` trocando `8080` por `8090` (e avise o pessoal do novo
endereço).

**"A APLICAÇÃO NÃO PODE SUBIR"** — leia a mensagem, ela diz exatamente o que
falta. Quase sempre é o `config.txt` apagado ou o PostgreSQL parado.

**PostgreSQL parado** — abra *Serviços* do Windows, procure `postgresql`,
clique em Iniciar.

**Ninguém da rede consegue abrir** — quase sempre é firewall. Rode `instalar.ps1`
como administrador de novo; ele só cria a regra que falta.

**Esqueceram a senha do administrador** — não há tela de recuperação. Fale com
o desenvolvedor: dá para redefinir direto no banco.

---

## O que NÃO fazer

- **Não apague `config.txt`.** Ele guarda a chave que assina os logins. Sem ele,
  ninguém entra.
- **Não mande `config.txt` por WhatsApp ou e-mail.** Tem senhas dentro.
- **Não instale duas vezes na mesma máquina.** Rodar `instalar.ps1` de novo é
  seguro: ele preserva banco, usuários e configuração existentes.
