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

Clique duas vezes em **`INSTALAR.bat`**.

O Windows vai pedir permissão de administrador — **aceite**. É o que permite
liberar a porta no firewall para os outros micros e agendar o backup; sem isso
a instalação termina pela metade.

> Não clique no `instalar.ps1` direto. Ele é o miolo, e o Windows barra script
> do PowerShell aberto assim. O `INSTALAR.bat` existe justamente para
> contornar essas duas travas sozinho.

O script vai pedir:

- a senha do `postgres` (a do passo 3)
- login, senha e nome do **administrador do sistema** — este é o acesso do Leo

E vai cuidar sozinho de: criar o banco, gerar a chave de segurança, liberar o
firewall e fazer o sistema subir junto com o Windows.

### 5. Confira

Não há nada para ligar: o instalador **já subiu o sistema** e o deixou
registrado para voltar sozinho toda vez que o computador ligar — inclusive
antes de alguém fazer login no Windows.

Abra o navegador em **<http://localhost:8080>** e entre com o login criado no
passo 4.

Se não abrir, clique em **`SISTEMA.bat`**. Ele diz na primeira linha se o
sistema está no ar, e o item 5 mostra as últimas mensagens dele.

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

### Ligar, desligar e conferir

O sistema sobe sozinho quando o computador liga, **antes de alguém entrar no
Windows**, e não abre janela nenhuma. Não existe mais aquela janela preta que
alguém podia fechar por engano e derrubar o escritório inteiro.

Para tudo que não seja *usar* o sistema, clique em **`SISTEMA.bat`**:

| Item | Serve para |
|---|---|
| Situação (no topo) | ver se está no ar, o endereço da rede e quando foi o último backup |
| Reiniciar | derrubar e subir de novo |
| Desligar / Ligar | manutenção |
| Ver o que o sistema disse | as últimas mensagens, com os erros em vermelho |
| Copiar agora | um backup fora do horário |

Ele pede permissão de administrador ao abrir. Isso é esperado: quem roda o
sistema é o próprio Windows, não um usuário logado.

O `iniciar.bat` continua na pasta, mas virou **modo diagnóstico** — ele roda o
sistema numa janela, mostrando tudo na tela. Só serve para quando o sistema se
recusa a subir e você quer ver o motivo na hora, sem ir atrás do arquivo de log.

### Backup — não pule esta parte

O instalador já deixou o backup **agendado para todo dia às 12:30**, e rodou um
na hora para provar que funciona. O horário é de propósito: o computador do
escritório está ligado ao meio-dia, enquanto uma tarefa de madrugada nunca
dispararia numa máquina que passa a noite desligada.

Para conferir se está mesmo acontecendo, abra `backups\historico.txt` — é uma
linha por execução, com `ok` ou `FALHOU`. Se as linhas pararem, alguma coisa
quebrou.

Precisando de uma cópia agora, fora do horário: clique em `backup.bat`.

Isso protege contra erro de digitação e apagão, **mas não contra o computador
queimar**. Oriente o cliente a copiar a pasta `backups` para um pendrive ou
para o Google Drive de vez em quando.

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

**"Porta 8080 já em uso"** — quase sempre é o próprio sistema, que já estava
rodando: confira em `SISTEMA.bat`. Se for mesmo outro programa, reinicie a
máquina.

**"A APLICAÇÃO NÃO PODE SUBIR"** — leia a mensagem, ela diz exatamente o que
falta. Quase sempre é o `config.properties` apagado ou o PostgreSQL parado.

**PostgreSQL parado** — abra *Serviços* do Windows, procure `postgresql`,
clique em Iniciar.

**Ninguém da rede consegue abrir** — quase sempre é firewall. Rode o
`INSTALAR.bat` de novo; ele só cria a regra que falta.

**Parou depois de reiniciar a máquina** — o sistema espera 90 segundos no
arranque para o PostgreSQL ficar pronto, e tenta de novo três vezes se falhar.
Se mesmo assim não voltar, abra `SISTEMA.bat`, item 5, e leia a última
mensagem.

**Esqueceram a senha do administrador** — não há tela de recuperação. Fale com
o desenvolvedor: dá para redefinir direto no banco.

---

## O que NÃO fazer

- **Não apague `config.properties`.** Ele guarda a chave que assina os logins. Sem ele,
  ninguém entra.
- **Não mande `config.properties` por WhatsApp ou e-mail.** Tem senhas dentro.
- **Não instale duas vezes na mesma máquina.** Rodar `instalar.ps1` de novo é
  seguro: ele preserva banco, usuários e configuração existentes.
