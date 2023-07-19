const fieldTypes = {
  radio: 'radio',
  input: 'input',
}

const formSpringMetadataRadio = [
  {
    type: fieldTypes.radio,
    labelName: 'Project',
    outputParamName: 'type'
  },
  {
    type: fieldTypes.radio,
    labelName: 'Language',
    outputParamName: 'language'
  },
  {
    type: fieldTypes.radio,
    labelName: 'Spring Boot',
    outputParamName: 'bootVersion'
  },
]

const formProjectMetadataRadio = [
  {
    type: fieldTypes.radio,
    labelName: 'Packaging',
    outputParamName: 'packaging'
  },
  {
    type: fieldTypes.radio,
    labelName: 'Java',
    outputParamName: 'javaVersion'
  },
]
