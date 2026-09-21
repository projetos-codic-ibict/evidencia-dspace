# EvidencIA

**Repositório Digital de Avaliação de Políticas Públicas.**

Este repositório contém o backend do EvidencIA, baseado no [DSpace](https://github.com/DSpace/DSpace). A interface web está no repositório [evidencia-dspace-angular](https://github.com/projetos-codic-ibict/evidencia-dspace-angular).

## Instalação recomendada

Para instalar e executar o EvidencIA, incluindo backend, interface e dependências, utilize o instalador Docker do projeto: [ibict-dspace-docker](https://github.com/projetos-codic-ibict/ibict-dspace-docker).

## Execução standalone para desenvolvimento

Para executar o backend localmente, configure o arquivo `dspace/config/local.cfg` de acordo com o seu ambiente (banco de dados, diretórios e serviços necessários) e execute:

```bash
mvn clean package
cd dspace/target/dspace-installer
ant fresh_install
# Em instalações já existentes, use: ant update
```

## Depuração no VS Code

Após a instalação standalone, crie ou ajuste `.vscode/launch.json` e inicie a configuração pelo painel **Run and Debug** do VS Code:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "EvidencIA Server",
      "type": "java",
      "request": "launch",
      "mainClass": "org.dspace.app.ServerBootApplication",
      "cwd": "${workspaceFolder}/dspace/target/dspace-installer",
      "vmArgs": "-Ddspace.dir=${workspaceFolder}/dspace/target/dspace-installer -Dlogging.config=file://${workspaceFolder}/dspace/target/dspace-installer/config/log4j2.xml",
      "projectName": "server-boot"
    }
  ]
}
```

## Créditos

O EvidencIA é uma customização do [DSpace](https://github.com/DSpace/DSpace), projeto original mantido pela comunidade DSpace e pela Lyrasis.

A funcionalidade de busca semântica e híbrida do EvidencIA utiliza a implementação do projeto LA Referencia/Lyrasis. A implementação de referência está documentada em:

- [DSpace — Semantic Search Overview](https://github.com/LA-Referencia-Lyrasis-Project/DSpace/blob/vector-search/docs/semantic-search-overview.md)
- [dspace-angular — Semantic Search Overview](https://github.com/LA-Referencia-Lyrasis-Project/dspace-angular/blob/vector-search/docs/semantic-search-overview.md)

## Licença

Este projeto é disponibilizado sob a [licença BSD 3-Clause](LICENSE), a mesma licença do DSpace original. As licenças de dependências de terceiros estão em [LICENSES_THIRD_PARTY](LICENSES_THIRD_PARTY).
